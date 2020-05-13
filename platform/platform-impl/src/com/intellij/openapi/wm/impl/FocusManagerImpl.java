// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.FocusRequestor;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.DirtyUI;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FocusManagerImpl extends IdeFocusManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(FocusManagerImpl.class);

  private final List<FocusRequestInfo> myRequests = new LinkedList<>();

  private final IdeEventQueue myQueue;

  private final EdtAlarm myFocusedComponentAlarm;
  private final EdtAlarm myForcedFocusRequestsAlarm;

  private final Set<FurtherRequestor> myValidFurtherRequestors = new HashSet<>();

  private final Set<ActionCallback> myTypeAheadRequestors = new HashSet<>();
  private boolean myTypeaheadEnabled = true;

  private final Map<Window, Component> myLastFocused = ContainerUtil.createWeakValueMap();
  private final Map<Window, Component> myLastFocusedAtDeactivation = ContainerUtil.createWeakValueMap();

  private DataContext myRunContext;

  private IdeFrame myLastFocusedFrame;

  public FocusManagerImpl() {
    myQueue = IdeEventQueue.getInstance();

    myFocusedComponentAlarm = new EdtAlarm();
    myForcedFocusRequestsAlarm = new EdtAlarm();

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ApplicationActivationListener.TOPIC, new AppListener());

    IdeEventQueue.getInstance().addDispatcher(e -> {
      if (e instanceof FocusEvent) {
        final FocusEvent fe = (FocusEvent)e;
        final Component c = fe.getComponent();
        if (c instanceof Window || c == null) return false;

        Component parent = ComponentUtil.findUltimateParent(c);
        if (parent instanceof IdeFrame) {
          LOG.assertTrue(parent instanceof Window);
          myLastFocused.put((Window)parent, c);
        }
      }
      else if (e instanceof WindowEvent) {
        Window window = ((WindowEvent)e).getWindow();
        if (e.getID() == WindowEvent.WINDOW_CLOSED) {
          if (window instanceof IdeFrame) {
            myLastFocused.remove(window);
            myLastFocusedAtDeactivation.remove(window);
          }
        }
      }

      return false;
    }, this);

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusedWindow", event -> {
      Object value = event.getNewValue();
      if (value instanceof IdeFrame) {
        LOG.assertTrue(value instanceof Window);
        myLastFocusedFrame = (IdeFrame)value;
      }
    });
  }

  @Override
  public IdeFrame getLastFocusedFrame() {
    return myLastFocusedFrame;
  }

  @Nullable
  @Override
  public Window getLastFocusedIdeWindow() {
    return (Window)myLastFocusedFrame;
  }

  @DirtyUI
  @Override
  public ActionCallback requestFocusInProject(@NotNull Component c, @Nullable Project project) {
    if (ApplicationManager.getApplication().isActive() || !Registry.is("suppress.focus.stealing.active.window.checks")) {
      logFocusRequest(c, project, false);
      c.requestFocus();
    }
    else {
      logFocusRequest(c, project, true);
      c.requestFocusInWindow();
    }
    return ActionCallback.DONE;
  }

  @Override
  @NotNull
  public ActionCallback requestFocus(@NotNull final Component c, final boolean forced) {
    logFocusRequest(c, null, false);
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
      myRequests.remove(0);
    }
  }

  public static IdeFocusManager getInstance() {
    return ApplicationManager.getApplication().getService(IdeFocusManager.class);
  }

  @DirtyUI
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
    AtomicBoolean immediate = new AtomicBoolean(true);
    doWhenFocusSettlesDown(() -> {
      if (immediate.get()) {
        if (!(runnable instanceof ExpirableRunnable) || !((ExpirableRunnable)runnable).isExpired()) {
          runnable.run();
        }
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> doWhenFocusSettlesDown(runnable, modality), modality);
    });
    immediate.set(false);
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
                                         return "Time: " + (System.currentTimeMillis() - currentTime) + "; cause: " + cause +
                                                "; runnable waiting for the focus change: " +
                                                IdeEventQueue.getInstance().runnablesWaitingForFocusChangeState();
                                       }
                                     },
                                     true).doWhenProcessed(() -> myTypeAheadRequestors.remove(done)));
  }

  @DirtyUI
  @Override
  public Component getFocusOwner() {
    assertDispatchThread();

    Component result = null;
    if (!ApplicationManager.getApplication().isActive()) {
      IdeFrame frame = getLastFocusedFrame();
      if (frame != null) {
        LOG.assertTrue(frame instanceof Window);
      }
      result = myLastFocusedAtDeactivation.get(frame);
    }
    else if (myRunContext != null) {
      result = (Component)myRunContext.getData(PlatformDataKeys.CONTEXT_COMPONENT.getName());
    }

    if (result == null) {
      result = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
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

  @DirtyUI
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
  public Component getLastFocusedFor(@Nullable Window frame) {
    assertDispatchThread();

    if (frame == null) {
      return null;
    }

    return myLastFocused.get(frame);
  }

  public void setLastFocusedAtDeactivation(@NotNull Window frame, @NotNull Component c) {
    myLastFocusedAtDeactivation.put(frame, c);
  }

  @Override
  public void toFront(JComponent c) {
    assertDispatchThread();

    if (c == null) return;

    final Window window = ComponentUtil.getParentOfType((Class<? extends Window>)Window.class, (Component)c);
    if (window != null && window.isShowing()) {
      doWhenFocusSettlesDown(() -> {
        if (ApplicationManager.getApplication().isActive()) {
          if (window instanceof JFrame && ((JFrame)window).getState() == Frame.ICONIFIED) {
            ((JFrame)window).setState(Frame.NORMAL);
          }
          else {
            window.toFront();
          }
        }
      });
    }
  }

  private static class FurtherRequestor implements FocusRequestor {
    private final IdeFocusManager myManager;
    private final Expirable myExpirable;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
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

  final static class EdtAlarm {
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

  private final class AppListener implements ApplicationActivationListener {
    @Override
    public void delayedApplicationDeactivated(@NotNull Window ideFrame) {
      Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      Component parent = UIUtil.findUltimateParent(owner);
      if (parent == ideFrame) {
        myLastFocusedAtDeactivation.put(ideFrame, owner);
      }
    }
  }

  @Override
  public JComponent getFocusTargetFor(@NotNull JComponent comp) {
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(comp);
  }

  @Override
  public Component getFocusedDescendantFor(@NotNull Component comp) {
    final Component focused = getFocusOwner();
    if (focused == null) return null;

    if (focused == comp || SwingUtilities.isDescendingFrom(focused, comp)) return focused;

    List<JBPopup> popups = AbstractPopup.getChildPopups(comp);
    for (JBPopup each : popups) {
      if (each.isFocused()) return focused;
    }

    return null;
  }

  @NotNull
  @Override
  public ActionCallback requestDefaultFocus(boolean forced) {
    Component toFocus = null;
    IdeFrame lastFocusedFrame = myLastFocusedFrame;
    if (lastFocusedFrame != null) {
      LOG.assertTrue(lastFocusedFrame instanceof Window);
      toFocus = myLastFocused.get(lastFocusedFrame);
      if (toFocus == null || !toFocus.isShowing()) {
        toFocus = getFocusTargetFor(lastFocusedFrame.getComponent());
      }
    }
    else {
      Optional<Component> toFocusOptional = Arrays.stream(Window.getWindows()).
        filter(window -> window instanceof RootPaneContainer).
        filter(window -> ((RootPaneContainer)window).getRootPane() != null).
        filter(window -> window.isActive()).
        findFirst().
        map(w -> getFocusTargetFor(((RootPaneContainer)w).getRootPane()));

      if (toFocusOptional.isPresent()) {
        toFocus = toFocusOptional.get();
      }
    }

    if (toFocus != null) {
      return requestFocusInProject(toFocus, null);
    }


    return ActionCallback.DONE;
  }

  @Override
  public boolean isFocusTransferEnabled() {
    if (Registry.is("focus.fix.lost.cursor")) {
      return true;
    }
    return ApplicationManager.getApplication().isActive() || !Registry.is("actionSystem.suspendFocusTransferIfApplicationInactive");
  }

  private static void assertDispatchThread() {
    if (Registry.is("actionSystem.assertFocusAccessFromEdt")) {
      EDT.assertIsEdt();
    }
  }

  private static void logFocusRequest(@NotNull Component c, @Nullable Project project, boolean inWindow) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("inWindow = %s, project = %s, component = %s", inWindow, project, c), new Throwable());
    }
  }
}
