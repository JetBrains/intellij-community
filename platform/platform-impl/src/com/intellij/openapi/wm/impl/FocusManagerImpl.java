/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.EdtRunnable;
import com.intellij.openapi.util.Expirable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.FocusTrackback;
import com.intellij.util.Alarm;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class FocusManagerImpl extends IdeFocusManager implements Disposable {

  private final Application myApp;

  private FocusCommand myRequestFocusCmd;
  private final ArrayList<FocusCommand> myFocusRequests = new ArrayList<FocusCommand>();

  private final ArrayList<KeyEvent> myToDispatchOnDone = new ArrayList<KeyEvent>();
  private int myFlushingIdleRequestsEntryCount = 0;

  private WeakReference<FocusCommand> myLastForcedRequest = new WeakReference<FocusCommand>(null);

  private FocusCommand myFocusCommandOnAppActivation;
  private ActionCallback myCallbackOnActivation;

  private final IdeEventQueue myQueue;
  private final KeyProcessorConext myKeyProcessorContext = new KeyProcessorConext();

  private long myCmdTimestamp;
  private long myForcedCmdTimestamp;

  final EdtAlarm myFocusedComponentAlaram;
  private final EdtAlarm myForcedFocusRequestsAlarm;

  private final EdtAlarm myIdleAlarm;
  private final Set<Runnable> myIdleRequests = new com.intellij.util.containers.HashSet<Runnable>();
  private final EdtRunnable myIdleRunnable = new EdtRunnable() {
    public void runEdt() {
      if (isFocusTransferReady() && !isIdleQueueEmpty()) {
        flushIdleRequests();
      }
      else {
        restartIdleAlarm();
      }
    }
  };

  private final WindowManager myWindowManager;

  private final Map<IdeFrame, Component> myLastFocused = new WeakHashMap<IdeFrame, Component>();
  private final Map<IdeFrame, Component> myLastFocusedAtDeactivation = new WeakHashMap<IdeFrame, Component>();

  public FocusManagerImpl(WindowManager wm) {
    myApp = ApplicationManager.getApplication();
    myQueue = IdeEventQueue.getInstance();
    myWindowManager = wm;

    myFocusedComponentAlaram = new EdtAlarm(this);
    myForcedFocusRequestsAlarm = new EdtAlarm(this);
    myIdleAlarm = new EdtAlarm(this);

    final AppListener myAppListener = new AppListener();
    myApp.addApplicationListener(myAppListener);

    IdeEventQueue.getInstance().addDispatcher(new IdeEventQueue.EventDispatcher() {
      public boolean dispatch(AWTEvent e) {
        if (e instanceof FocusEvent) {
          final FocusEvent fe = (FocusEvent)e;
          final Component c = fe.getComponent();
          if (c instanceof Window || c == null) return false;

          Component parent = UIUtil.findUltimateParent(c);
          if (parent instanceof IdeFrame) {
            myLastFocused.put((IdeFrame)parent, c);
          }
        } else if (e instanceof WindowEvent) {
          if (e.getID() == WindowEvent.WINDOW_CLOSED) {
            Window wnd = ((WindowEvent)e).getWindow();
            if (wnd instanceof IdeFrame) {
              myLastFocused.remove(wnd);
              myLastFocusedAtDeactivation.remove(wnd);
            }
          }
        }

        return false;
      }
    }, this);

  }

  public ActionCallback requestFocus(final Component c, final boolean forced) {
    return requestFocus(new FocusCommand.ByComponent(c), forced);
  }

  public ActionCallback requestFocus(final FocusCommand command, final boolean forced) {
    final ActionCallback result = new ActionCallback();

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

  private void _requestFocus(final FocusCommand command, final boolean forced, final ActionCallback result) {
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

          myCmdTimestamp++;
          if (forced) {
            myForcedCmdTimestamp++;
          }

          command.run().doWhenDone(new Runnable() {
            public void run() {
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  result.setDone();
                }
              });
            }
          }).doWhenRejected(new Runnable() {
            public void run() {
              result.setRejected();
            }
          }).doWhenProcessed(new Runnable() {
            public void run() {
              resetCommand(command, false);

              if (forced) {
                myForcedFocusRequestsAlarm.addRequest(new EdtRunnable() {
                  public void runEdt() {
                    setLastEffectiveForcedRequest(null);
                  }
                }, 250);
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
      !myApp.isActive() && (!canExecuteOnInactiveApplication(cmd) && Registry.is("actionSystem.suspendFocusTransferIfApplicationInactive"));

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
  }

  @Nullable
  private FocusCommand getLastEffectiveForcedRequest() {
    if (myLastForcedRequest == null) return null;
    final FocusCommand request = myLastForcedRequest.get();
    return request != null && !request.isExpired() ? request : null;
  }

  boolean isUnforcedRequestAllowed() {
    return getLastEffectiveForcedRequest() == null;
  }

  public static FocusManagerImpl getInstance() {
    return (FocusManagerImpl)ApplicationManager.getApplication().getComponent(IdeFocusManager.class);
  }

  public void dispose() {
  }

  private class KeyProcessorConext implements KeyEventProcessor.Context {
    public java.util.List<KeyEvent> getQueue() {
      return myToDispatchOnDone;
    }

    public void dispatch(final java.util.List<KeyEvent> events) {
      doWhenFocusSettlesDown(new Runnable() {
        public void run() {
          myToDispatchOnDone.addAll(events);
          restartIdleAlarm();
        }
      });
    }
  }

  public void doWhenFocusSettlesDown(@NotNull final Runnable runnable) {
    final boolean needsRestart = isIdleQueueEmpty();
    myIdleRequests.add(runnable);

    if (isFocusTransferReady()) {
      flushIdleRequests();
    } else {
      if (needsRestart) {
        restartIdleAlarm();
      }
    }
  }

  private void restartIdleAlarm() {
    myIdleAlarm.cancelAllRequests();
    myIdleAlarm.addRequest(myIdleRunnable, Registry.intValue("actionSystem.focusIdleTimeout"));
  }

  private void flushIdleRequests() {
    try {
      myFlushingIdleRequestsEntryCount++;

      final KeyEvent[] events = myToDispatchOnDone.toArray(new KeyEvent[myToDispatchOnDone.size()]);
      if (events.length > 0) {
        IdeEventQueue.getInstance().getKeyEventDispatcher().resetState();
      }

      boolean keyWasPressed = false;

      for (KeyEvent each : events) {
        if (!isFocusTransferReady()) break;

        if (!keyWasPressed) {
          if (each.getID() == KeyEvent.KEY_PRESSED) {
            keyWasPressed = true;
          }
          else {
            myToDispatchOnDone.remove(each);
            continue;
          }
        }

        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner == null) {
          owner = JOptionPane.getRootFrame();
        }

        KeyEvent keyEvent = new KeyEvent(owner, each.getID(), each.getWhen(), each.getModifiersEx(), each.getKeyCode(), each.getKeyChar(),
                                         each.getKeyLocation());

        myToDispatchOnDone.remove(each);

        if (owner != null && SwingUtilities.getWindowAncestor(owner) != null) {
          IdeEventQueue.getInstance().dispatchEvent(keyEvent);
        }
        else {
          myQueue._dispatchEvent(keyEvent, true);
        }
      }


      if (isPendingKeyEventsRedispatched()) {
        final Runnable[] all = myIdleRequests.toArray(new Runnable[myIdleRequests.size()]);
        myIdleRequests.clear();
        for (Runnable each : all) {
          each.run();
        }
      }
    }
    finally {
      myFlushingIdleRequestsEntryCount--;
      if (!isIdleQueueEmpty()) {
        restartIdleAlarm();
      }
    }
  }

  public boolean isFocusTransferReady() {
    invalidateFocusRequestsQueue();

    if (!myFocusRequests.isEmpty()) return false;
    if (myQueue == null) return true;

    return !myQueue.isSuspendMode() && !myQueue.hasFocusEventsPending();
  }

  private void invalidateFocusRequestsQueue() {
    if (myFocusRequests.size() == 0) return;

    FocusCommand[] requests = myFocusRequests.toArray(new FocusCommand[myFocusRequests.size()]);
    boolean wasChanged = false;
    for (FocusCommand each : requests) {
      if (each.isExpired()) {
        resetCommand(each, true);
        wasChanged = true;
      }
    }

    if (wasChanged && myFocusRequests.size() == 0) {
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
    if (!Registry.is("actionSystem.fixLostTyping")) return false;

    if (myFlushingIdleRequestsEntryCount > 0) return false;

    if (!isFocusTransferReady() || !isPendingKeyEventsRedispatched()) {
      for (FocusCommand each : myFocusRequests) {
        final KeyEventProcessor processor = each.getProcessor();
        if (processor != null) {
          final Boolean result = processor.dispatch(e, myKeyProcessorContext);
          if (result != null) {
            return result.booleanValue();
          }
        }
      }

      myToDispatchOnDone.add(e);
      restartIdleAlarm();

      return true;
    }
    else {
      return false;
    }
  }

  public void suspendKeyProcessingUntil(final ActionCallback done) {
    requestFocus(new FocusCommand(done) {
      public ActionCallback run() {
        return done;
      }
    }.saveAllocation(), true);
  }

  public Expirable getTimestamp(final boolean trackOnlyForcedCommands) {
    return new Expirable() {
      long myOwnStamp = trackOnlyForcedCommands ? myForcedCmdTimestamp : myCmdTimestamp;

      public boolean isExpired() {
        return myOwnStamp < (trackOnlyForcedCommands ? myForcedCmdTimestamp : myCmdTimestamp);
      }
    };
  }

  @Override
  public FocusRequestor getFurtherRequestor() {
    return new FurtherRequestor(this, getTimestamp(true));
  }

  private static class FurtherRequestor implements FocusRequestor {

    private IdeFocusManager myManager;
    private Expirable myExpirable;

    private FurtherRequestor(IdeFocusManager manager, Expirable expirable) {
      myManager = manager;
      myExpirable = expirable;
    }

    @NotNull
    @Override
    public ActionCallback requestFocus(@NotNull Component c, boolean forced) {
      return myExpirable.isExpired() ? new ActionCallback.Rejected() : myManager.requestFocus(c, forced);
    }

    @NotNull
    @Override
    public ActionCallback requestFocus(@NotNull FocusCommand command, boolean forced) {
      return myExpirable.isExpired() ? new ActionCallback.Rejected() : myManager.requestFocus(command, forced);
    }
  }


  static class EdtAlarm {
    private final Alarm myAlarm;

    private EdtAlarm(Disposable parent) {
      myAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, parent);
    }

    public void cancelAllRequests() {
      myAlarm.cancelAllRequests();
    }

    public void addRequest(EdtRunnable runnable, int delay) {
      myAlarm.addRequest(runnable, delay);
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

  private class AppListener extends ApplicationAdapter {

    @Override
    public void applicationDeactivated(IdeFrame ideFrame) {
      final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      Component parent = UIUtil.findUltimateParent(owner);

      if (parent == ideFrame) {
        myLastFocusedAtDeactivation.put(ideFrame, owner);
      }
    }

    @Override
    public void applicationActivated(final IdeFrame ideFrame) {
      final FocusCommand cmd = myFocusCommandOnAppActivation;
      ActionCallback callback = myCallbackOnActivation;
      myFocusCommandOnAppActivation = null;
      myCallbackOnActivation = null;

      if (cmd != null) {
        requestFocus(cmd, true).notify(callback).doWhenRejected(new Runnable() {
          public void run() {
            focusLastFocusedComponent(ideFrame);
          }
        });
      }
      else {
        focusLastFocusedComponent(ideFrame);
      }
    }

    private void focusLastFocusedComponent(IdeFrame ideFrame) {
      final KeyboardFocusManager mgr = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      if (mgr.getFocusOwner() == null) {
        Component c = myLastFocusedAtDeactivation.get(ideFrame);
        if (c == null || !c.isShowing()) {
          c = myLastFocused.get(ideFrame);
        }

        if (c != null && c.isShowing()) {
          requestFocus(c, false);
        }
      }

      myLastFocusedAtDeactivation.remove(ideFrame);
    }
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

    java.util.List<JBPopup> popups = FocusTrackback.getChildPopups(comp);
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
    return new ActionCallback.Done();
  }

  @Override
  public boolean isFocusTransferEnabled() {
    return myApp.isActive() || !Registry.is("actionSystem.suspendFocusTransferIfApplicationInactive");
  }
}
