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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.impl.ServiceManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

public class FocusManagerImpl extends IdeFocusManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(FocusManagerImpl.class);
  private static final UiActivity FOCUS = new UiActivity.Focus("awtFocusRequest");
  private static final UiActivity TYPEAHEAD = new UiActivity.Focus("typeahead");

  private final Application myApp;

  private final boolean isInternalMode = ApplicationManagerEx.getApplicationEx().isInternal();
  private final LinkedList<FocusRequestInfo> myRequests = new LinkedList<>();

  private final IdeEventQueue myQueue;

  private final EdtAlarm myFocusedComponentAlarm;
  private final EdtAlarm myForcedFocusRequestsAlarm;

  private final Set<FurtherRequestor> myValidFurtherRequestors = new HashSet<>();

  private final Set<ActionCallback> myTypeAheadRequestors = new HashSet<>();
  private final UiActivityMonitor myActivityMonitor;
  private boolean myTypeaheadEnabled = true;

  private final Map<IdeFrame, Component> myLastFocused = ContainerUtil.createWeakValueMap();
  private final Map<IdeFrame, Component> myLastFocusedAtDeactivation = ContainerUtil.createWeakValueMap();

  private DataContext myRunContext;

  private IdeFrame myLastFocusedFrame;

  @SuppressWarnings("UnusedParameters")  // the dependencies are needed to ensure correct loading order
  public FocusManagerImpl(ServiceManagerImpl serviceManager, WindowManager wm, UiActivityMonitor monitor) {
    myApp = ApplicationManager.getApplication();
    myQueue = IdeEventQueue.getInstance();
    myActivityMonitor = monitor;

    myFocusedComponentAlarm = new EdtAlarm();
    myForcedFocusRequestsAlarm = new EdtAlarm();

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
    if (ApplicationManagerEx.getApplicationEx().isActive() || !Registry.is("suppress.focus.stealing")) {
      c.requestFocus();
    } else {
      c.requestFocusInWindow();
    }
    return ActionCallback.DONE;
  }

  @Override
  @NotNull
  public ActionCallback requestFocus(@NotNull final Component c, final boolean forced) {
    c.requestFocus();
    return ActionCallback.DONE;
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

  @Override
  public void doWhenFocusSettlesDown(@NotNull ExpirableRunnable runnable) {
    doWhenFocusSettlesDown((Runnable)runnable);
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull final Runnable runnable) {
    myQueue.executeWhenAllFocusEventsLeftTheQueue(runnable);
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull Runnable runnable, @NotNull ModalityState modality) {
    doWhenFocusSettlesDown(runnable);
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
      myTypeAheadRequestors.remove(done);
    }));
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
      result =  KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }

    if (result == null) {
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

  private class AppListener implements ApplicationActivationListener {

    @Override
    public void applicationActivated(final IdeFrame ideFrame) {}

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

  public List<JBPopup> getChildPopups(@NotNull final Component component) {
    return AbstractPopup.all.toStrongList().stream().filter(popup -> {
      Component owner = popup.getOwner();
      while (owner != null) {
        if (owner.equals(component)) {
          return true;
        }
        owner = owner.getParent();
      }
      return false;
    }).collect(Collectors.toList());
  }

  @Override
  public Component getFocusedDescendantFor(Component comp) {
    final Component focused = getFocusOwner();
    if (focused == null) return null;

    if (focused == comp || SwingUtilities.isDescendingFrom(focused, comp)) return focused;

    List<JBPopup> popups = getChildPopups(comp);
    for (JBPopup each : popups) {
      if (each.isFocused()) return focused;
    }

    return null;
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
      if (ApplicationManagerEx.getApplicationEx().isActive() || !Registry.is("suppress.focus.stealing")) {
        toFocus.requestFocus();
      } else {
        toFocus.requestFocusInWindow();
      }
      return ActionCallback.DONE;
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
}
