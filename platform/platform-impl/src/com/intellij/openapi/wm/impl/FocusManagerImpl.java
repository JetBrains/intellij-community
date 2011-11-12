/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.ide.UiActivityMonitor;
import com.intellij.internal.focus.FocusTracesAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.ui.FocusTrackback;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;

public class FocusManagerImpl extends IdeFocusManager implements Disposable {

  private static final String FOCUS = "focus";
  private static final String TYPEAHEAD = "typeahead";

  private final Application myApp;

  private FocusCommand myRequestFocusCmd;
  private final ArrayList<FocusCommand> myFocusRequests = new ArrayList<FocusCommand>();

  private final ArrayList<KeyEvent> myToDispatchOnDone = new ArrayList<KeyEvent>();

  private WeakReference<FocusCommand> myLastForcedRequest = new WeakReference<FocusCommand>(null);

  private FocusCommand myFocusCommandOnAppActivation;
  private ActionCallback myCallbackOnActivation;
  private final boolean isInternalMode = ApplicationManagerEx.getApplicationEx().isInternal();
  private final List<FocusRequestInfo> myRequests = new ArrayList<FocusRequestInfo>();

  private final IdeEventQueue myQueue;
  private final KeyProcessorConext myKeyProcessorContext = new KeyProcessorConext();

  private long myCmdTimestamp;
  private long myForcedCmdTimestamp;

  final EdtAlarm myFocusedComponentAlaram;
  private final EdtAlarm myForcedFocusRequestsAlarm;

  private final SimpleTimer myTimer = SimpleTimer.newInstance("FocusManager timer");
  
  private final EdtAlarm myIdleAlarm;
  private final Set<Runnable> myIdleRequests = new LinkedHashSet<Runnable>();

  private boolean myFlushWasDelayedToFixFocus;
  private ExpirableRunnable myFocusRevalidator;

  private final Set<FurtherRequestor> myValidFurtherRequestors = new HashSet<FurtherRequestor>();

  private final Set<ActionCallback> myTypeAheadRequestors = new HashSet<ActionCallback>();
  private final UiActivityMonitor myActivityMonitor;
  private boolean myTypeaheadEnabled = true;
  private int myModalityStateForLastForcedRequest;


  private class IdleRunnable extends EdtRunnable {
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

  private final Map<IdeFrame, WeakReference<Component>> myLastFocused = new HashMap<IdeFrame, WeakReference<Component>>();
  private final Map<IdeFrame, WeakReference<Component>> myLastFocusedAtDeactivation = new HashMap<IdeFrame, WeakReference<Component>>();

  private DataContext myRunContext;

  private final Map<Integer, Integer> myModalityCount2FlushCount = new HashMap<Integer, Integer>();

  private IdeFrame myLastFocusedFrame;

  public FocusManagerImpl(WindowManager wm) {
    myApp = ApplicationManager.getApplication();
    myQueue = IdeEventQueue.getInstance();
    myActivityMonitor = UiActivityMonitor.getInstance();

    myFocusedComponentAlaram = new EdtAlarm();
    myForcedFocusRequestsAlarm = new EdtAlarm();
    myIdleAlarm = new EdtAlarm();

    final AppListener myAppListener = new AppListener();
    myApp.getMessageBus().connect().subscribe(ApplicationActivationListener.TOPIC, myAppListener);

    IdeEventQueue.getInstance().addDispatcher(new IdeEventQueue.EventDispatcher() {
      public boolean dispatch(AWTEvent e) {
        if (e instanceof FocusEvent) {
          final FocusEvent fe = (FocusEvent)e;
          final Component c = fe.getComponent();
          if (c instanceof Window || c == null) return false;

          Component parent = SwingUtilities.getWindowAncestor(c);

          if (parent instanceof IdeFrame) {
            myLastFocused.put((IdeFrame)parent, new WeakReference<Component>(c));
          }
        } else if (e instanceof WindowEvent) {
          Window wnd = ((WindowEvent)e).getWindow();
          if (e.getID() == WindowEvent.WINDOW_CLOSED) {
            if (wnd instanceof IdeFrame) {
              myLastFocused.remove((IdeFrame)wnd);
              myLastFocusedAtDeactivation.remove((IdeFrame)wnd);
            }
          }
        }

        return false;
      }
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

  @NotNull
  public ActionCallback requestFocus(@NotNull final Component c, final boolean forced) {
    return requestFocus(new FocusCommand.ByComponent(c), forced);
  }

  @NotNull
  public ActionCallback requestFocus(@NotNull final FocusCommand command, final boolean forced) {
    assertDispatchThread();

    if (isInternalMode) {
      recordCommand(command, new Throwable(), forced);
    }
    final ActionCallback result = new ActionCallback();

    myActivityMonitor.addActivity(FOCUS, ModalityState.any());
    if (!forced) {
      if (!myFocusRequests.contains(command)) {
        myFocusRequests.add(command);
      }

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          resetUnforcedCommand(command);
          _requestFocus(command, forced, result);
        }
      });
    }
    else {
      _requestFocus(command, forced, result);
    }

    result.doWhenProcessed(new Runnable() {
      public void run() {
        restartIdleAlarm();
      }
    });

    return result;
  }

  public List<FocusRequestInfo> getRequests() {
    return myRequests;
  }

  private void recordCommand(FocusCommand command, Throwable trace, boolean forced) {
    if (FocusTracesAction.isActive()) {
      myRequests.add(new FocusRequestInfo(command.getDominationComponent(), trace, forced));
    }
  }

  private void _requestFocus(final FocusCommand command, final boolean forced, final ActionCallback result) {
    result.doWhenProcessed(new Runnable() {
      @Override
      public void run() {
        maybeRemoveFocusActivity();
      }
    });
    
    if (checkForRejectOrByPass(command, forced, result)) return;

    setCommand(command);
    command.setCallback(result);

    if (forced) {
      myForcedFocusRequestsAlarm.cancelAllRequests();
      setLastEffectiveForcedRequest(command);
    }

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (checkForRejectOrByPass(command, forced, result)) return;

        if (myRequestFocusCmd == command) {
          final ActionCallback.TimedOut focusTimeout =
            new ActionCallback.TimedOut(Registry.intValue("actionSystem.commandProcessingTimeout"),
                                        "Focus command timed out, cmd=" + command, command.getAllocation(), true) {
              @Override
              protected void onTimeout() {
                forceFinishFocusSettledown(command, result);
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

          command.run().doWhenDone(new Runnable() {
            public void run() {
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  resetCommand(command, false);
                  result.setDone();
                }
              });
            }
          }).doWhenRejected(new Runnable() {
            public void run() {
              result.setRejected();
              resetCommand(command, true);
            }
          }).doWhenProcessed(new Runnable() {
            public void run() {
              if (forced) {
                myForcedFocusRequestsAlarm.addRequest(new SetLastEffectiveRunnable(), 250);
              }
            }
          }).notify(focusTimeout);
        }
        else {
          rejectCommand(command, result);
        }
      }
    });
  }

  private void maybeRemoveFocusActivity() {
    if (isFocusTransferReady()) {
      myActivityMonitor.removeActivity(FOCUS);
    }
  }

  private boolean checkForRejectOrByPass(final FocusCommand cmd, final boolean forced, final ActionCallback result) {
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

    return false;
  }

  private void setCommand(FocusCommand command) {
    myRequestFocusCmd = command;

    if (!myFocusRequests.contains(command)) {
      myFocusRequests.add(command);
    }
  }

  private void resetCommand(FocusCommand cmd, boolean reject) {
    if (cmd == myRequestFocusCmd) {
      myRequestFocusCmd = null;
    }

    final KeyEventProcessor processor = cmd.getProcessor();
    if (processor != null) {
      processor.finish(myKeyProcessorContext);
    }

    myFocusRequests.remove(cmd);

    if (reject) {
      ActionCallback cb = cmd.getCallback();
      if (cb != null && !cb.isProcessed()) {
        cmd.getCallback().setRejected();
      }
    }
  }

  private void resetUnforcedCommand(FocusCommand cmd) {
    myFocusRequests.remove(cmd);
  }

  private static boolean canExecuteOnInactiveApplication(FocusCommand cmd) {
    return cmd.canExecuteOnInactiveApp();
  }

  private void setLastEffectiveForcedRequest(FocusCommand command) {
    myLastForcedRequest = new WeakReference<FocusCommand>(command);
    myModalityStateForLastForcedRequest = getCurrentModalityCount();
  }

  @Nullable
  private FocusCommand getLastEffectiveForcedRequest() {
    if (myLastForcedRequest == null) return null;
    final FocusCommand request = myLastForcedRequest.get();
    return request != null && !request.isExpired() ? request : null;
  }

  boolean isUnforcedRequestAllowed() {
    if (getLastEffectiveForcedRequest() == null) return true;
    return myModalityStateForLastForcedRequest != getCurrentModalityCount();
  }

  public static FocusManagerImpl getInstance() {
    return (FocusManagerImpl)ApplicationManager.getApplication().getComponent(IdeFocusManager.class);
  }

  public void dispose() {
    myForcedFocusRequestsAlarm.cancelAllRequests();
    myFocusedComponentAlaram.cancelAllRequests();
  }

  private class KeyProcessorConext implements KeyEventProcessor.Context {
    public List<KeyEvent> getQueue() {
      return myToDispatchOnDone;
    }

    public void dispatch(final List<KeyEvent> events) {
      doWhenFocusSettlesDown(new Runnable() {
        public void run() {
          myToDispatchOnDone.addAll(events);
          restartIdleAlarm();
        }
      });
    }
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull ExpirableRunnable runnable) {
    doWhenFocusSettlesDown((Runnable)runnable);
  }

  public void doWhenFocusSettlesDown(@NotNull final Runnable runnable) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (isFlushingIdleRequests()) {
          SwingUtilities.invokeLater(this);
          return;
        }

        if (myRunContext != null) {
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
      }
    });
  }

  private void restartIdleAlarm() {
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
          } else {
            toDispatch = true;
          }

          myToDispatchOnDone.remove(each);
          if (!toDispatch) {
            continue;
          }


          KeyEvent keyEvent = new KeyEvent(owner, each.getID(), each.getWhen(), each.getModifiersEx(), each.getKeyCode(), each.getKeyChar(),
                                           each.getKeyLocation());


          if (owner != null && SwingUtilities.getWindowAncestor(owner) != null) {
            IdeEventQueue.getInstance().dispatchEvent(keyEvent);
          }
          else {
            myQueue._dispatchEvent(keyEvent, true);
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
        } else if (!focusOk && myFlushWasDelayedToFixFocus) {
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
  }

  private boolean isIdleQueueEmpty() {
    return isPendingKeyEventsRedispatched() && myIdleRequests.isEmpty();
  }

  private boolean isPendingKeyEventsRedispatched() {
    return myToDispatchOnDone.isEmpty();
  }

  public boolean dispatch(KeyEvent e) {
    if (!isTypeaheadEnabled()) return false;
    if (isFlushingIdleRequests()) return false;

    if (!isFocusTransferReady() || !isPendingKeyEventsRedispatched() || !myTypeAheadRequestors.isEmpty()) {
      for (FocusCommand each : myFocusRequests) {
        final KeyEventProcessor processor = each.getProcessor();
        if (processor != null) {
          final Boolean result = processor.dispatch(e, myKeyProcessorContext);
          if (result != null) {
            if (result.booleanValue()) {
              myActivityMonitor.addActivity(TYPEAHEAD, ModalityState.any());
              return true;
            } else {
              return false;
            }
          }
        }
      }

      myToDispatchOnDone.add(e);
      myActivityMonitor.addActivity(TYPEAHEAD, ModalityState.any());
      
      restartIdleAlarm();

      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public void setTypeaheadEnabled(boolean enabled) {
    myTypeaheadEnabled = enabled;
  }

  private boolean isTypeaheadEnabled() {
    return Registry.is("actionSystem.fixLostTyping") && myTypeaheadEnabled;
  }

  @Override
  public void typeAheadUntil(final ActionCallback done) {
    assertDispatchThread();

    if (!isTypeaheadEnabled()) return;

    myTypeAheadRequestors.add(done);
    done.notify(new ActionCallback.TimedOut(Registry.intValue("actionSystem.commandProcessingTimeout"),
                                            "Typeahead request blocked",
                                            new Exception(),
                                            true).doWhenProcessed(new Runnable() {
      @Override
      public void run() {
        if (myTypeAheadRequestors.remove(done)) {
          restartIdleAlarm();
        }
      }
    }));
  }

  private boolean isFlushingIdleRequests() {
    return getFlushingIdleRequests() > 0;
  }

  private int getFlushingIdleRequests() {
    int currentModalityCount = getCurrentModalityCount();
    if (myModalityCount2FlushCount.containsKey(currentModalityCount)) {
      return myModalityCount2FlushCount.get(currentModalityCount);
    } else {
      return 0;
    }
  }

  private void incFlushingRequests(int delta, final int currentModalityCount) {
    if (myModalityCount2FlushCount.containsKey(currentModalityCount)) {
      Integer requests = myModalityCount2FlushCount.get(currentModalityCount);
      myModalityCount2FlushCount.put(currentModalityCount, requests + delta);
    } else {
      myModalityCount2FlushCount.put(currentModalityCount, Integer.valueOf(delta));
    }
  }

  private int getCurrentModalityCount() {
    int modalDialogs = 0;
    Window[] windows = Window.getWindows();
    for (Window each : windows) {
      if (each instanceof Dialog) {
        Dialog eachDialog = (Dialog)each;
        if (eachDialog.isModal() && eachDialog.isShowing()) {
          modalDialogs++;
        }
      } else if (each instanceof JWindow) {
        final JBPopup popup = (JBPopup)((JWindow)each).getRootPane().getClientProperty(JBPopup.KEY);
        if (popup != null && popup.isModalContext()) {
          modalDialogs++;
        }
      }
    }
    Iterator<Integer> modalityCounts = myModalityCount2FlushCount.keySet().iterator();
    while (modalityCounts.hasNext()) {
      Integer eachModalityCount = modalityCounts.next();
      if (eachModalityCount > modalDialogs) {
        modalityCounts.remove();
      }
    }

    return modalDialogs;
  }

  public void suspendKeyProcessingUntil(@NotNull final ActionCallback done) {
    typeAheadUntil(done);
 }

  public Expirable getTimestamp(final boolean trackOnlyForcedCommands) {
    assertDispatchThread();

    return new Expirable() {
      long myOwnStamp = trackOnlyForcedCommands ? myForcedCmdTimestamp : myCmdTimestamp;

      public boolean isExpired() {
        return myOwnStamp < (trackOnlyForcedCommands ? myForcedCmdTimestamp : myCmdTimestamp);
      }
    };
  }

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
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        myFocusRevalidator = runnable;
        restartIdleAlarm();
      }
    });
  }

  @Override
  public Component getFocusOwner() {
    assertDispatchThread();

    Component result = null;
    if (!ApplicationManager.getApplication().isActive()) {
      final WeakReference<Component> ref = myLastFocusedAtDeactivation.get(getLastFocusedFrame());
      if (ref != null) {
        result = ref.get();
      }
    } else if (myRunContext != null) {
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
  public void runOnOwnContext(DataContext context, Runnable runnable) {
    assertDispatchThread();

    myRunContext = context;
    try {
      runnable.run();
    } finally {
      myRunContext = null;
    }
  }

  @Override
  public Component getLastFocusedFor(IdeFrame frame) {
    assertDispatchThread();

    WeakReference<Component> ref = myLastFocused.get(frame);
    return ref != null ? ref.get() : null;
  }

  @Override
  public void toFront(JComponent c) {
    assertDispatchThread();

    if (c == null) return;

    final Window window = UIUtil.getParentOfType(Window.class, c);
    if (window != null && window.isShowing()) {
      doWhenFocusSettlesDown(new Runnable() {
        @Override
        public void run() {
          if (ApplicationManager.getApplication().isActive()) {
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

    private FurtherRequestor(IdeFocusManager manager, Expirable expirable) {
      myManager = manager;
      myExpirable = expirable;
      if (Registry.is("ide.debugMode")) {
        myAllocation = new Exception();
      }
    }

    @NotNull
    @Override
    public ActionCallback requestFocus(@NotNull Component c, boolean forced) {
      final ActionCallback result = isExpired() ? new ActionCallback.Rejected() : myManager.requestFocus(c, forced);
      result.doWhenProcessed(new Runnable() {
        @Override
        public void run() {
          Disposer.dispose(FurtherRequestor.this);
        }
      });
      return result;
    }

    private boolean isExpired() {
      return myExpirable.isExpired() || myDisposed;
    }

    @NotNull
    @Override
    public ActionCallback requestFocus(@NotNull FocusCommand command, boolean forced) {
      return isExpired() ? new ActionCallback.Rejected() : myManager.requestFocus(command, forced);
    }

    @Override
    public void dispose() {
      myDisposed = true;
    }
  }


  class EdtAlarm {

    private final Set<EdtRunnable> myRequests = new HashSet<EdtRunnable>();
    
    public void cancelAllRequests() {
      for (EdtRunnable each : myRequests) {
        each.expire();
      }
      myRequests.clear();
    }

    public void addRequest(EdtRunnable runnable, int delay) {
      myRequests.add(runnable);
      myTimer.setUp(runnable, delay);
    }
  }

  private void forceFinishFocusSettledown(FocusCommand cmd, ActionCallback cmdCallback) {
    rejectCommand(cmd, cmdCallback);
  }


  private void rejectCommand(FocusCommand cmd, ActionCallback callback) {
    resetCommand(cmd, true);
    resetUnforcedCommand(cmd);

    callback.setRejected();
  }

  private class AppListener implements ApplicationActivationListener {

    @Override
    public void applicationDeactivated(IdeFrame ideFrame) {
      final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      Component parent = UIUtil.findUltimateParent(owner);

      if (parent == ideFrame) {
        myLastFocusedAtDeactivation.put(ideFrame, new WeakReference<Component>(owner));
      }
    }

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

    private void focusLastFocusedComponent(IdeFrame ideFrame) {
      final KeyboardFocusManager mgr = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      if (mgr.getFocusOwner() == null) {
        Component c = getComponent(myLastFocusedAtDeactivation, ideFrame);
        if (c == null || !c.isShowing()) {
          c = getComponent(myLastFocused, ideFrame);
        }

        final boolean mouseEventAhead = IdeEventQueue.isMouseEventAhead(null);
        if (c != null && c.isShowing() && !mouseEventAhead) {
          final LayoutFocusTraversalPolicyExt policy = LayoutFocusTraversalPolicyExt.findWindowPolicy(c);
          if (policy != null) {
            policy.setNoDefaultComponent(true, FocusManagerImpl.this);
          }
          requestFocus(c, false).doWhenProcessed(new Runnable() {
            @Override
            public void run() {
              if (policy != null) {
                policy.setNoDefaultComponent(false, FocusManagerImpl.this);
              }
            }
          });
        }
      }

      myLastFocusedAtDeactivation.remove(ideFrame);
    }
  }

  @Nullable
  private static Component getComponent(Map<IdeFrame, WeakReference<Component>> map, IdeFrame frame) {
    WeakReference<Component> ref = map.get(frame);
    if (ref == null) return null;
    return ref.get();
  }

  @Override
  public JComponent getFocusTargetFor(@NotNull JComponent comp) {
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(comp);
  }

  @Override
  public Component getFocusedDescendantFor(Component comp) {
    final Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
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

  @Override
  public ActionCallback requestDefaultFocus(boolean forced) {
    Component toFocus = null;
    if (myLastFocusedFrame != null) {
      WeakReference<Component> c = myLastFocused.get(myLastFocusedFrame);
      if (c != null && c.get() != null && c.get().isShowing()) {
        toFocus = c.get();
      } else {
        toFocus = getFocusTargetFor(myLastFocusedFrame.getComponent());
      }
    } else {
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
      return requestFocus(new FocusCommand.ByComponent(toFocus).setToInvalidateRequestors(false), forced);      
    }
    
    
    return new ActionCallback.Done();
  }

  @Override
  public boolean isFocusTransferEnabled() {
    return myApp.isActive() || !Registry.is("actionSystem.suspendFocusTransferIfApplicationInactive");
  }

  private static void assertDispatchThread() {
    if (Registry.is("actionSystem.assertFocusAccessFromEdt")) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
  }

  private class SetLastEffectiveRunnable extends EdtRunnable {
    public void runEdt() {
      setLastEffectiveForcedRequest(null);
    }
  }
}
