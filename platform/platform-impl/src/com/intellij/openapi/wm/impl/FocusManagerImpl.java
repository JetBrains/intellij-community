/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.UiActivity;
import com.intellij.ide.UiActivityMonitor;
import com.intellij.internal.focus.FocusTracesAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.impl.ServiceManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.reference.SoftReference;
import com.intellij.ui.FocusTrackback;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FocusManagerImpl extends IdeFocusManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(FocusManagerImpl.class);
  private static final UiActivity FOCUS = new UiActivity.Focus("awtFocusRequest");
  private static final UiActivity TYPEAHEAD = new UiActivity.Focus("typeahead");

  private final Application myApp;

  private FocusCommand myRequestFocusCmd;
  private final List<FocusCommand> myFocusRequests = new ArrayList<>();

  private final List<KeyEvent> myToDispatchOnDone = new ArrayList<>();

  private Reference<FocusCommand> myLastForcedRequest;

  private FocusCommand myFocusCommandOnAppActivation;
  private ActionCallback myCallbackOnActivation;
  private final boolean isInternalMode = ApplicationManagerEx.getApplicationEx().isInternal();
  private final LinkedList<FocusRequestInfo> myRequests = new LinkedList<>();

  private final IdeEventQueue myQueue;
  private final KeyProcessorContext myKeyProcessorContext = new KeyProcessorContext();

  private long myCmdTimestamp;
  private long myForcedCmdTimestamp;

  private final EdtAlarm myFocusedComponentAlarm;
  private final EdtAlarm myForcedFocusRequestsAlarm;

  private final EdtAlarm myIdleAlarm;
  private final Set<Runnable> myIdleRequests = new LinkedHashSet<>();

  private boolean myFlushWasDelayedToFixFocus;
  private ExpirableRunnable myFocusRevalidator;

  private final Set<FurtherRequestor> myValidFurtherRequestors = new HashSet<>();

  private final Set<ActionCallback> myTypeAheadRequestors = new HashSet<>();
  private final UiActivityMonitor myActivityMonitor;
  private boolean myTypeaheadEnabled = true;
  private int myModalityStateForLastForcedRequest;


  private class IdleRunnable extends EdtRunnable {
    @Override
    public void runEdt() {
      if (canFlushIdleRequests()) {
        flushIdleRequests();
      }
      else {
        if (processFocusRevalidation()) {
          if (isFocusTransferReady()) {
            flushIdleRequests();
          }
        }

        restartIdleAlarm();
      }
    }
  }

  private boolean canFlushIdleRequests() {
    Component focusOwner = getFocusOwner();
    return isFocusTransferReady()
           && !isIdleQueueEmpty()
           && !IdeEventQueue.getInstance().isDispatchingFocusEvent()
           && !(focusOwner == null && (!myValidFurtherRequestors.isEmpty() || myFocusRevalidator != null && !myFocusRevalidator.isExpired()));
  }

  private final Map<IdeFrame, Component> myLastFocused = ContainerUtil.createWeakValueMap();
  private final Map<IdeFrame, Component> myLastFocusedAtDeactivation = ContainerUtil.createWeakValueMap();

  private DataContext myRunContext;

  private final TIntIntHashMap myModalityCount2FlushCount = new TIntIntHashMap();

  private IdeFrame myLastFocusedFrame;

  @SuppressWarnings("UnusedParameters")  // the dependencies are needed to ensure correct loading order
  public FocusManagerImpl(ServiceManagerImpl serviceManager, WindowManager wm, UiActivityMonitor monitor) {
    myApp = ApplicationManager.getApplication();
    myQueue = IdeEventQueue.getInstance();
    myActivityMonitor = monitor;

    myFocusedComponentAlarm = new EdtAlarm();
    myForcedFocusRequestsAlarm = new EdtAlarm();
    myIdleAlarm = new EdtAlarm();

    final AppListener myAppListener = new AppListener();
    myApp.getMessageBus().connect().subscribe(ApplicationActivationListener.TOPIC, myAppListener);

    IdeEventQueue.getInstance().addDispatcher(e -> {
      if (e instanceof FocusEvent) {
        final FocusEvent fe = (FocusEvent)e;
        final Component c = fe.getComponent();
        if (c instanceof Window || c == null) return false;

        Component parent = UIUtil.findUltimateParent(c);

        if (parent instanceof IdeFrame) {
          myLastFocused.put((IdeFrame)parent, c);
        }
      }
      else if (e instanceof WindowEvent) {
        Window wnd = ((WindowEvent)e).getWindow();
        if (e.getID() == WindowEvent.WINDOW_CLOSED) {
          if (wnd instanceof IdeFrame) {
            myLastFocused.remove(wnd);
            myLastFocusedAtDeactivation.remove(wnd);
          }
        }
      }

      return false;
    }, this);

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusedWindow", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getNewValue() instanceof IdeFrame) {
          myLastFocusedFrame = (IdeFrame)evt.getNewValue();
        }
      }
    });
  }

  @Override
  public IdeFrame getLastFocusedFrame() {
    return myLastFocusedFrame;
  }

  public ActionCallback requestFocusInProject(@NotNull Component c, @Nullable Project project) {
    return requestFocus(new FocusCommand.ByComponent(c, c, project, new Exception()), false);
  }

  @Override
  @NotNull
  public ActionCallback requestFocus(@NotNull final Component c, final boolean forced) {
    return requestFocus(new FocusCommand.ByComponent(c, new Exception()), forced);
  }

  @Override
  @NotNull
  public ActionCallback requestFocus(@NotNull final FocusCommand command, final boolean forced) {
    assertDispatchThread();

    if (isInternalMode) {
      recordCommand(command, new Throwable(), forced);
    }
    final ActionCallback result = new ActionCallback();

    myActivityMonitor.addActivity(FOCUS, ModalityState.any());
    if (!forced) {

      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
        if (!myFocusRequests.contains(command)) {
          myFocusRequests.add(command);
        }
      });

      SwingUtilities.invokeLater(() -> {
        resetUnforcedCommand(command);
        _requestFocus(command, forced, result);
      });
    }
    else {
      _requestFocus(command, forced, result);
    }

    result.doWhenProcessed(() -> restartIdleAlarm());

    return result;
  }

  @NotNull
  public List<FocusRequestInfo> getRequests() {
    return myRequests;
  }

  public void recordFocusRequest(Component c, boolean forced) {
    myRequests.add(new FocusRequestInfo(c, new Throwable(), forced));
    if (myRequests.size() > 200) {
      myRequests.removeFirst();
    }
  }

  private void recordCommand(@NotNull FocusCommand command, @NotNull Throwable trace, boolean forced) {
    if (FocusTracesAction.isActive()) {
      recordFocusRequest(command.getDominationComponent(), forced);
    }
  }

  private void _requestFocus(@NotNull final FocusCommand command, final boolean forced, @NotNull final ActionCallback result) {
    result.doWhenProcessed(() -> maybeRemoveFocusActivity());
    
    if (checkForRejectOrByPass(command, forced, result)) return;

    setCommand(command);
    command.setCallback(result);

    if (forced) {
      myForcedFocusRequestsAlarm.cancelAllRequests();
      setLastEffectiveForcedRequest(command);
    }

    SwingUtilities.invokeLater(() -> {
      if (checkForRejectOrByPass(command, forced, result)) return;

      if (myRequestFocusCmd == command) {
        final TimedOutCallback focusTimeout =
          new TimedOutCallback(Registry.intValue("actionSystem.commandProcessingTimeout"),
                                      "Focus command timed out, cmd=" + command, command.getAllocation(), true) {
            @Override
            protected void onTimeout() {
              forceFinishFocusSettleDown(command, result);
            }
          };

        if (command.invalidatesRequestors()) {
          myCmdTimestamp++;
        }
        revalidateFurtherRequestors();
        if (forced) {
          if (command.invalidatesRequestors()) {
            myForcedCmdTimestamp++;
          }
          revalidateFurtherRequestors();
        }

        command.setForced(forced);
        command.run().doWhenDone(() -> UIUtil.invokeLaterIfNeeded(() -> {
          resetCommand(command, false);
          result.setDone();
        })).doWhenRejected(() -> {
          result.setRejected();
          resetCommand(command, true);
        }).doWhenProcessed(() -> {
          if (forced) {
            myForcedFocusRequestsAlarm.addRequest(new SetLastEffectiveRunnable(), 250);
          }
        }).notify(focusTimeout);
      }
      else {
        rejectCommand(command, result);
      }
    });
  }

  private void maybeRemoveFocusActivity() {
    if (isFocusTransferReady()) {
      myActivityMonitor.removeActivity(FOCUS);
    }
  }

  private boolean checkForRejectOrByPass(@NotNull FocusCommand cmd, final boolean forced, @NotNull ActionCallback result) {
    if (cmd.isExpired()) {
      rejectCommand(cmd, result);
      return true;
    }

    final FocusCommand lastRequest = getLastEffectiveForcedRequest();

    if (!forced && !isUnforcedRequestAllowed()) {
      if (cmd.equals(lastRequest)) {
        resetCommand(cmd, false);
        result.setDone();
      }
      else {
        rejectCommand(cmd, result);
      }
      return true;
    }


    if (lastRequest != null && lastRequest.dominatesOver(cmd)) {
      rejectCommand(cmd, result);
      return true;
    }

    if (!Registry.is("focus.fix.lost.cursor")) {
      boolean doNotExecuteBecauseAppIsInactive =
        !myApp.isActive() && !canExecuteOnInactiveApplication(cmd) && Registry.is("actionSystem.suspendFocusTransferIfApplicationInactive");

      if (doNotExecuteBecauseAppIsInactive) {
        if (myCallbackOnActivation != null) {
          myCallbackOnActivation.setRejected();
          if (myFocusCommandOnAppActivation != null) {
            resetCommand(myFocusCommandOnAppActivation, true);
          }
        }

        myFocusCommandOnAppActivation = cmd;
        myCallbackOnActivation = result;

        return true;
      }
    }

    return false;
  }

  private void setCommand(@NotNull final FocusCommand command) {
    myRequestFocusCmd = command;

    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      if (!myFocusRequests.contains(command)) {
        myFocusRequests.add(command);
      }
    });
  }

  private void resetCommand(@NotNull final FocusCommand cmd, boolean reject) {

    assertDispatchThread();

    if (cmd == myRequestFocusCmd) {
      myRequestFocusCmd = null;
    }

    final KeyEventProcessor processor = cmd.getProcessor();
    if (processor != null) {
      processor.finish(myKeyProcessorContext);
    }

    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> myFocusRequests.remove(cmd));

    if (reject) {
      ActionCallback cb = cmd.getCallback();
      if (cb != null && !cb.isProcessed()) {
        cmd.getCallback().setRejected();
      }
    }
  }

  private void resetUnforcedCommand(@NotNull final FocusCommand cmd) {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> myFocusRequests.remove(cmd));
  }

  private static boolean canExecuteOnInactiveApplication(@NotNull FocusCommand cmd) {
    return cmd.canExecuteOnInactiveApp();
  }

  private void setLastEffectiveForcedRequest(@Nullable FocusCommand command) {
    myLastForcedRequest = command == null ? null : new WeakReference<>(command);
    myModalityStateForLastForcedRequest = getCurrentModalityCount();
  }

  @Nullable
  private FocusCommand getLastEffectiveForcedRequest() {
    final FocusCommand request = SoftReference.dereference(myLastForcedRequest);
    return request != null && !request.isExpired() ? request : null;
  }

  boolean isUnforcedRequestAllowed() {
    if (getLastEffectiveForcedRequest() == null) return true;
    return myModalityStateForLastForcedRequest != getCurrentModalityCount();
  }

  public static FocusManagerImpl getInstance() {
    return (FocusManagerImpl)ApplicationManager.getApplication().getComponent(IdeFocusManager.class);
  }

  @Override
  public void dispose() {
    myForcedFocusRequestsAlarm.cancelAllRequests();
    myFocusedComponentAlarm.cancelAllRequests();
    for (FurtherRequestor requestor : myValidFurtherRequestors) {
      Disposer.dispose(requestor);
    }
    myValidFurtherRequestors.clear();
  }

  private class KeyProcessorContext implements KeyEventProcessor.Context {
    @Override
    @NotNull
    public List<KeyEvent> getQueue() {
      return myToDispatchOnDone;
    }

    @Override
    public void dispatch(@NotNull final List<KeyEvent> events) {
      doWhenFocusSettlesDown(() -> {
        myToDispatchOnDone.addAll(events);
        restartIdleAlarm();
      });
    }
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull ExpirableRunnable runnable) {
    doWhenFocusSettlesDown((Runnable)runnable);
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull final Runnable runnable) {
    boolean invokedOnEdt = ApplicationManager.getApplication().isDispatchThread();
    UIUtil.invokeLaterIfNeeded(() -> {
      if (isFlushingIdleRequests()) {
        myIdleRequests.add(runnable);
        return;
      }

      if (myRunContext != null || invokedOnEdt && canFlushIdleRequests()) {
        flushRequest(runnable);
        return;
      }

      final boolean needsRestart = isIdleQueueEmpty();
      if (myIdleRequests.contains(runnable)) {
        myIdleRequests.remove(runnable);
        myIdleRequests.add(runnable);
      } else {
        myIdleRequests.add(runnable);
      }

      if (canFlushIdleRequests()) {
        flushIdleRequests();
      }
      else {
        if (needsRestart) {
          restartIdleAlarm();
        }
      }
    });
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull Runnable runnable, @NotNull ModalityState modality) {
    AtomicBoolean immediate = new AtomicBoolean(true);
    doWhenFocusSettlesDown(() -> {
      if (immediate.get()) {
        flushRequest(runnable);
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> doWhenFocusSettlesDown(runnable, modality), modality);
    });
    immediate.set(false);
  }

  private void restartIdleAlarm() {
    if (!ApplicationManager.getApplication().isActive()) return;
    myIdleAlarm.cancelAllRequests();
    myIdleAlarm.addRequest(new IdleRunnable(), Registry.intValue("actionSystem.focusIdleTimeout"));
  }

  private void flushIdleRequests() {
    int currentModalityCount = getCurrentModalityCount();
    try {
      incFlushingRequests(1, currentModalityCount);

      if (!isTypeaheadEnabled()) {
        myToDispatchOnDone.clear();
        myTypeAheadRequestors.clear();
      }

      if (!myToDispatchOnDone.isEmpty() && myTypeAheadRequestors.isEmpty()) {
        final KeyEvent[] events = myToDispatchOnDone.toArray(new KeyEvent[myToDispatchOnDone.size()]);

        IdeEventQueue.getInstance().getKeyEventDispatcher().resetState();

        for (int eachIndex = 0; eachIndex < events.length; eachIndex++) {
          if (!isFocusTransferReady()) {
            break;
          }

          KeyEvent each = events[eachIndex];

          Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
          if (owner == null) {
            owner = JOptionPane.getRootFrame();
          }

          boolean metaKey =
            each.getKeyCode() == KeyEvent.VK_ALT ||
            each.getKeyCode() == KeyEvent.VK_CONTROL ||
            each.getKeyCode() == KeyEvent.VK_SHIFT ||
            each.getKeyCode() == KeyEvent.VK_META;

          boolean toDispatch = false;
          if (!metaKey && (each.getID() == KeyEvent.KEY_RELEASED || each.getID() == KeyEvent.KEY_TYPED)) {
            for (int i = 0; i < eachIndex; i++) {
              final KeyEvent prev = events[i];
              if (prev == null) continue;

              if (prev.getID() == KeyEvent.KEY_PRESSED) {
                if (prev.getKeyCode() == each.getKeyCode() || prev.getKeyChar() == each.getKeyChar()) {
                  toDispatch = true;
                  events[i] = null;
                  break;
                }
              }
            }
          }
          else {
            toDispatch = true;
          }

          myToDispatchOnDone.remove(each);
          if (!toDispatch) {
            continue;
          }


          KeyEvent keyEvent = new KeyEvent(owner, each.getID(), each.getWhen(), each.getModifiersEx(), each.getKeyCode(), each.getKeyChar(),
                                           each.getKeyLocation());


          if (SwingUtilities.getWindowAncestor(owner) != null) {
            IdeEventQueue.getInstance().dispatchEvent(keyEvent);
          }
          else {
            ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(
              () -> myQueue._dispatchEvent(keyEvent, true));
          }
        }

        if (myToDispatchOnDone.isEmpty() && myTypeAheadRequestors.isEmpty()) {
          myActivityMonitor.removeActivity(TYPEAHEAD);
        }
      }

      if (!isFocusBeingTransferred()) {
        boolean focusOk = getFocusOwner() != null;
        if (!focusOk && !myFlushWasDelayedToFixFocus) {
          IdeEventQueue.getInstance().fixStickyFocusedComponents(null);
          myFlushWasDelayedToFixFocus = true;
        }
        else if (!focusOk) {
          myFlushWasDelayedToFixFocus = false;
        }

        if (canFlushIdleRequests() && getFlushingIdleRequests() <= 1 && (focusOk || !myFlushWasDelayedToFixFocus)) {
          myFlushWasDelayedToFixFocus = false;
          flushNow();
        }
      }
    }
    finally {
      incFlushingRequests(-1, currentModalityCount);
      if (!isIdleQueueEmpty()) {
        restartIdleAlarm();
      }

      maybeRemoveFocusActivity();
    }
  }

  private boolean processFocusRevalidation() {
    ExpirableRunnable revalidator = myFocusRevalidator;
    myFocusRevalidator = null;
    
    if (revalidator != null && !revalidator.isExpired()) {
      revalidator.run();
      return true;
    }
    
    return false;
  }

  private void flushNow() {
    final Runnable[] all = myIdleRequests.toArray(new Runnable[myIdleRequests.size()]);
    myIdleRequests.clear();
    for (int i = 0; i < all.length; i++) {
      flushRequest(all[i]);
      if (isFocusBeingTransferred()) {
        for (int j = i + 1; j < all.length; j++) {
          myIdleRequests.add(all[j]);
        }
        break;
      }
    }
    
    maybeRemoveFocusActivity();
  }

  private static void flushRequest(Runnable each) {
    if (each == null) return;
    if (each instanceof Expirable) {
      if (!((Expirable)each).isExpired()) {
        each.run();
      }
    } else {
      each.run();
    }
  }

  public boolean isFocusTransferReady() {
    assertDispatchThread();

    if (myRunContext != null) return true;

    invalidateFocusRequestsQueue();

    if (!myFocusRequests.isEmpty()) return false;
    if (myQueue == null) return true;

    return !myQueue.isSuspendMode() && !myQueue.hasFocusEventsPending();
  }

  private void invalidateFocusRequestsQueue() {
    assertDispatchThread();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      if (myFocusRequests.isEmpty()) return;

      FocusCommand[] requests = myFocusRequests.toArray(new FocusCommand[myFocusRequests.size()]);
      boolean wasChanged = false;
      for (FocusCommand each : requests) {
        if (each.isExpired()) {
          resetCommand(each, true);
          wasChanged = true;
        }
      }

      if (wasChanged && myFocusRequests.isEmpty()) {
        restartIdleAlarm();
      }
    });
  }

  private boolean isIdleQueueEmpty() {
    return isPendingKeyEventsRedispatched() && myIdleRequests.isEmpty();
  }

  private boolean isPendingKeyEventsRedispatched() {
    return myToDispatchOnDone.isEmpty();
  }

  @Override
  public boolean dispatch(@NotNull KeyEvent e) {
    if (!isTypeaheadEnabled()) return false;
    if (isFlushingIdleRequests()) return false;

    assertDispatchThread();

    if (!isFocusTransferReady() || !isPendingKeyEventsRedispatched() || !myTypeAheadRequestors.isEmpty()) {
      for (FocusCommand each : myFocusRequests) {
        final KeyEventProcessor processor = each.getProcessor();
        if (processor != null) {
          final Boolean result = processor.dispatch(e, myKeyProcessorContext);
          if (result != null) {
            if (result.booleanValue()) {
              myActivityMonitor.addActivity(TYPEAHEAD, ModalityState.any());
              return true;
            }
            return false;
          }
        }
      }

      myToDispatchOnDone.add(e);
      myActivityMonitor.addActivity(TYPEAHEAD, ModalityState.any());

      restartIdleAlarm();

      return true;
    }
    return false;
  }

  @Override
  public void setTypeaheadEnabled(boolean enabled) {
    myTypeaheadEnabled = enabled;
  }

  private boolean isTypeaheadEnabled() {
    return Registry.is("actionSystem.fixLostTyping") && myTypeaheadEnabled;
  }

  @Override
  public void typeAheadUntil(@NotNull ActionCallback callback, @NotNull String cause) {
    if (!isTypeaheadEnabled()) return;

    final long currentTime = System.currentTimeMillis();
    final ActionCallback done;
    if (!Registry.is("type.ahead.logging.enabled")) {
      done = callback;
    }
    else {
      final String id = new Exception().getStackTrace()[2].getClassName();
      //LOG.setLevel(Level.ALL);
      final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:ss:SSS", Locale.US);
      LOG.info(dateFormat.format(System.currentTimeMillis()) + "\tStarted:  " + id);
      done = new ActionCallback();
      callback.doWhenDone(() -> {
        done.setDone();
        LOG.info(dateFormat.format(System.currentTimeMillis()) + "\tDone:     " + id);
      });
      callback.doWhenRejected(() -> {
        done.setRejected();
        LOG.info(dateFormat.format(System.currentTimeMillis()) + "\tRejected: " + id);
      });
    }
    assertDispatchThread();

    myTypeAheadRequestors.add(done);
    done.notify(new TimedOutCallback(Registry.intValue("actionSystem.commandProcessingTimeout"),
                                            "Typeahead request blocked",
                                            new Exception() {
                                              @Override
                                              public String getMessage() {
                                                return "Time: " + (System.currentTimeMillis() - currentTime) + "; cause: " + cause;
                                              }
                                            },
                                            true).doWhenProcessed(() -> {
                                              if (myTypeAheadRequestors.remove(done)) {
                                                restartIdleAlarm();
                                              }
                                            }));
  }

  private boolean isFlushingIdleRequests() {
    return getFlushingIdleRequests() > 0;
  }

  private int getFlushingIdleRequests() {
    int currentModalityCount = getCurrentModalityCount();
    return myModalityCount2FlushCount.get(currentModalityCount);
  }

  private void incFlushingRequests(int delta, final int currentModalityCount) {
    if (myModalityCount2FlushCount.containsKey(currentModalityCount)) {
      myModalityCount2FlushCount.adjustValue(currentModalityCount, delta);
    }
    else {
      myModalityCount2FlushCount.put(currentModalityCount, delta);
    }
  }

  private int getCurrentModalityCount() {
    int modalityCount = 0;
    Window[] windows = Window.getWindows();
    for (Window each : windows) {
      if (!each.isShowing()) continue;

      if (each instanceof Dialog) {
        Dialog eachDialog = (Dialog)each;
        if (eachDialog.isModal()) {
          modalityCount++;
        }
        else if (each instanceof JDialog) {
          if (isModalContextPopup(((JDialog)each).getRootPane())) {
            modalityCount++;
          }
        }
      }
      else if (each instanceof JWindow) {
        JRootPane rootPane = ((JWindow)each).getRootPane();
        if (isModalContextPopup(rootPane)) {
          modalityCount++;
        }
      }
    }
    final int finalModalityCount = modalityCount;
    myModalityCount2FlushCount.retainEntries(new TIntIntProcedure() {
      @Override
      public boolean execute(int eachModalityCount, int flushCount) {
        return eachModalityCount <= finalModalityCount;
      }
    });

    return modalityCount;
  }

  private static boolean isModalContextPopup(@NotNull JRootPane rootPane) {
    final JBPopup popup = (JBPopup)rootPane.getClientProperty(JBPopup.KEY);
    return popup != null && popup.isModalContext();
  } 

  @NotNull
  @Override
  public Expirable getTimestamp(final boolean trackOnlyForcedCommands) {
    assertDispatchThread();

    return new Expirable() {
      long myOwnStamp = trackOnlyForcedCommands ? myForcedCmdTimestamp : myCmdTimestamp;

      @Override
      public boolean isExpired() {
        return myOwnStamp < (trackOnlyForcedCommands ? myForcedCmdTimestamp : myCmdTimestamp);
      }
    };
  }

  @NotNull
  @Override
  public FocusRequestor getFurtherRequestor() {
    assertDispatchThread();

    FurtherRequestor requestor = new FurtherRequestor(this, getTimestamp(true));
    myValidFurtherRequestors.add(requestor);
    revalidateFurtherRequestors();
    return requestor;
  }

  private void revalidateFurtherRequestors() {
    Iterator<FurtherRequestor> requestorIterator = myValidFurtherRequestors.iterator();
    while (requestorIterator.hasNext()) {
      FurtherRequestor each = requestorIterator.next();
      if (each.isExpired()) {
        requestorIterator.remove();
        Disposer.dispose(each);
      }
    }
  }
  
  @Override
  public void revalidateFocus(@NotNull final ExpirableRunnable runnable) {
    SwingUtilities.invokeLater(() -> {
      myFocusRevalidator = runnable;
      restartIdleAlarm();
    });
  }

  @Override
  public Component getFocusOwner() {
    assertDispatchThread();

    Component result = null;
    if (!ApplicationManager.getApplication().isActive()) {
      result = myLastFocusedAtDeactivation.get(getLastFocusedFrame());
    }
    else if (myRunContext != null) {
      result = (Component)myRunContext.getData(PlatformDataKeys.CONTEXT_COMPONENT.getName());
    }

    if (result == null) {
      result =  isFocusBeingTransferred() ? null : KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }

    final boolean meaninglessOwner = UIUtil.isMeaninglessFocusOwner(result);
    if (result == null && !isFocusBeingTransferred() || meaninglessOwner) {
      final Component permOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
      if (permOwner != null) {
        result = permOwner;
      }
      
      if (UIUtil.isMeaninglessFocusOwner(result)) {
        result = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      }
    }

    return result;
  }

  @Override
  public void runOnOwnContext(@NotNull DataContext context, @NotNull Runnable runnable) {
    assertDispatchThread();

    myRunContext = context;
    try {
      runnable.run();
    }
    finally {
      myRunContext = null;
    }
  }

  @Override
  public Component getLastFocusedFor(IdeFrame frame) {
    assertDispatchThread();

    return myLastFocused.get(frame);
  }

  public void setLastFocusedAtDeactivation(@NotNull IdeFrame frame, @NotNull Component c) {
    myLastFocusedAtDeactivation.put(frame, c);
  }

  @Override
  public void toFront(JComponent c) {
    assertDispatchThread();

    if (c == null) return;

    final Window window = UIUtil.getParentOfType(Window.class, c);
    if (window != null && window.isShowing()) {
      doWhenFocusSettlesDown(() -> {
        if (ApplicationManager.getApplication().isActive()) {
          if (window instanceof JFrame && ((JFrame)window).getState() == Frame.ICONIFIED) {
            ((JFrame)window).setState(Frame.NORMAL);
          } else {
            window.toFront();
          }
        }
      });
    }
  }

  private static class FurtherRequestor implements FocusRequestor {
    private final IdeFocusManager myManager;
    private final Expirable myExpirable;
    private Throwable myAllocation;
    private boolean myDisposed;

    private FurtherRequestor(@NotNull IdeFocusManager manager, @NotNull Expirable expirable) {
      myManager = manager;
      myExpirable = expirable;
      if (Registry.is("ide.debugMode")) {
        myAllocation = new Exception();
      }
    }

    @NotNull
    @Override
    public ActionCallback requestFocus(@NotNull Component c, boolean forced) {
      final ActionCallback result = isExpired() ? ActionCallback.REJECTED : myManager.requestFocus(c, forced);
      result.doWhenProcessed(() -> Disposer.dispose(this));
      return result;
    }

    private boolean isExpired() {
      return myExpirable.isExpired() || myDisposed;
    }

    @NotNull
    @Override
    public ActionCallback requestFocus(@NotNull FocusCommand command, boolean forced) {
      return isExpired() ? ActionCallback.REJECTED : myManager.requestFocus(command, forced);
    }

    @Override
    public void dispose() {
      myDisposed = true;
    }
  }


  static class EdtAlarm {
    private final Set<EdtRunnable> myRequests = new HashSet<>();
    
    public void cancelAllRequests() {
      for (EdtRunnable each : myRequests) {
        each.expire();
      }
      myRequests.clear();
    }

    public void addRequest(@NotNull EdtRunnable runnable, int delay) {
      myRequests.add(runnable);
      EdtExecutorService.getScheduledExecutorInstance().schedule(runnable, delay, TimeUnit.MILLISECONDS);
    }
  }

  private void forceFinishFocusSettleDown(@NotNull FocusCommand cmd, @NotNull ActionCallback cmdCallback) {
    rejectCommand(cmd, cmdCallback);
  }


  private void rejectCommand(@NotNull FocusCommand cmd, @NotNull ActionCallback callback) {
    resetCommand(cmd, true);
    resetUnforcedCommand(cmd);

    callback.setRejected();
  }

  private class AppListener implements ApplicationActivationListener {

    @Override
    public void applicationActivated(final IdeFrame ideFrame) {
      final FocusCommand cmd = myFocusCommandOnAppActivation;
      ActionCallback callback = myCallbackOnActivation;
      myFocusCommandOnAppActivation = null;
      myCallbackOnActivation = null;

      if (cmd != null) {
        requestFocus(cmd, true).notify(callback);
      } else {
        focusLastFocusedComponent(ideFrame);
      }
    }

    @Override
    public void delayedApplicationDeactivated(IdeFrame ideFrame) {
        final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        Component parent = UIUtil.findUltimateParent(owner);

        if (parent == ideFrame) {
          myLastFocusedAtDeactivation.put(ideFrame, owner);
        }
    }

    private void focusLastFocusedComponent(IdeFrame ideFrame) {
      final KeyboardFocusManager mgr = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      if (mgr.getFocusOwner() == null) {
        Component c = getComponent(myLastFocusedAtDeactivation, ideFrame);
        if (c == null || !c.isShowing()) {
          c = getComponent(myLastFocusedAtDeactivation, ideFrame);
        }

        final boolean mouseEventAhead = IdeEventQueue.isMouseEventAhead(null);
        if (c != null && c.isShowing() && !mouseEventAhead) {
          final LayoutFocusTraversalPolicyExt policy = LayoutFocusTraversalPolicyExt.findWindowPolicy(c);
          if (policy != null) {
            policy.setNoDefaultComponent(true, FocusManagerImpl.this);
          }
          requestFocus(c, false).doWhenProcessed(() -> {
            if (policy != null) {
              policy.setNoDefaultComponent(false, FocusManagerImpl.this);
            }
          });
        }
      }

      myLastFocusedAtDeactivation.remove(ideFrame);
    }
  }

  @Nullable
  private static Component getComponent(@NotNull Map<IdeFrame, Component> map, IdeFrame frame) {
    return map.get(frame);
  }

  @Override
  public JComponent getFocusTargetFor(@NotNull JComponent comp) {
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(comp);
  }

  @Override
  public Component getFocusedDescendantFor(Component comp) {
    final Component focused = getFocusOwner();
    if (focused == null) return null;

    if (focused == comp || SwingUtilities.isDescendingFrom(focused, comp)) return focused;

    List<JBPopup> popups = FocusTrackback.getChildPopups(comp);
    for (JBPopup each : popups) {
      if (each.isFocused()) return focused;
    }

    return null;
  }

  @Override
  public boolean isFocusBeingTransferred() {
    return !isFocusTransferReady();
  }

  @NotNull
  @Override
  public ActionCallback requestDefaultFocus(boolean forced) {
    Component toFocus = null;
    if (myLastFocusedFrame != null) {
      toFocus = myLastFocused.get(myLastFocusedFrame);
      if (toFocus == null || !toFocus.isShowing()) {
        toFocus = getFocusTargetFor(myLastFocusedFrame.getComponent());
      }
    }
    else {
      Window[] windows = Window.getWindows();
      for (Window each : windows) {
        if (each.isActive()) {
          if (each instanceof JFrame) {
            toFocus = getFocusTargetFor(((JFrame)each).getRootPane());
            break;
          } else if (each instanceof JDialog) {
            toFocus = getFocusTargetFor(((JDialog)each).getRootPane());
            break;
          } else if (each instanceof JWindow) {
            toFocus = getFocusTargetFor(((JWindow)each).getRootPane());
            break;
          }          
        }
      }
    } 
    
    if (toFocus != null) {
      return requestFocus(new FocusCommand.ByComponent(toFocus, new Exception()).setToInvalidateRequestors(false), forced);
    }
    
    
    return ActionCallback.DONE;
  }

  @Override
  public boolean isFocusTransferEnabled() {
    if (Registry.is("focus.fix.lost.cursor")) return true;
    return myApp.isActive() || !Registry.is("actionSystem.suspendFocusTransferIfApplicationInactive");
  }

  private static void assertDispatchThread() {
    if (Registry.is("actionSystem.assertFocusAccessFromEdt")) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
  }

  private class SetLastEffectiveRunnable extends EdtRunnable {
    @Override
    public void runEdt() {
      setLastEffectiveForcedRequest(null);
    }
  }
}
