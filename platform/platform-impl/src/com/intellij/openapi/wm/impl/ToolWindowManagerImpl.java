// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.ActivateToolWindowAction;
import com.intellij.ide.actions.MaximizeActiveDialogAction;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.*;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.*;
import com.intellij.openapi.wm.impl.commands.*;
import com.intellij.ui.BalloonImpl;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.PositionTracker;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.intellij.lang.annotations.JdkConstants;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@State(
  name = "ToolWindowManager",
  defaultStateAsResource = true,
  storages = @Storage(value = StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)
)
public class ToolWindowManagerImpl extends ToolWindowManagerEx implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.ToolWindowManagerImpl");

  private final Project myProject;
  private final WindowManagerEx myWindowManager;
  private final EventDispatcher<ToolWindowManagerListener> myDispatcher = EventDispatcher.create(ToolWindowManagerListener.class);
  private final DesktopLayout myLayout = new DesktopLayout();
  private final Map<String, InternalDecorator> myId2InternalDecorator = new HashMap<>();
  private final Map<String, FloatingDecorator> myId2FloatingDecorator = new HashMap<>();
  private final Map<String, WindowedDecorator> myId2WindowedDecorator = new HashMap<>();
  private final Map<String, StripeButton> myId2StripeButton = new HashMap<>();
  private final Map<String, FocusWatcher> myId2FocusWatcher = new HashMap<>();

  private final MyToolWindowPropertyChangeListener myToolWindowPropertyChangeListener = new MyToolWindowPropertyChangeListener();
  private final InternalDecoratorListener myInternalDecoratorListener = new MyInternalDecoratorListener();

  private final ActiveStack myActiveStack = new ActiveStack();
  private final SideStack mySideStack = new SideStack();

  private ToolWindowsPane myToolWindowsPane;
  private IdeFrameImpl myFrame;
  private DesktopLayout myLayoutToRestoreLater;
  @NonNls private static final String EDITOR_ELEMENT = "editor";
  @NonNls private static final String ACTIVE_ATTR_VALUE = "active";
  @NonNls private static final String FRAME_ELEMENT = "frame";
  @NonNls private static final String X_ATTR = "x";
  @NonNls private static final String Y_ATTR = "y";
  @NonNls private static final String WIDTH_ATTR = "width";
  @NonNls private static final String HEIGHT_ATTR = "height";
  @NonNls private static final String EXTENDED_STATE_ATTR = "extended-state";
  @NonNls private static final String LAYOUT_TO_RESTORE = "layout-to-restore";

  private final Map<String, Balloon> myWindow2Balloon = new HashMap<>();

  private KeyState myCurrentState = KeyState.waiting;
  private final Alarm myWaiterForSecondPress = new Alarm();
  private final Runnable mySecondPressRunnable = () -> {
    if (myCurrentState != KeyState.hold) {
      resetHoldState();
    }
  };

  boolean isToolWindowRegistered(@NotNull String id) {
    return myLayout.isToolWindowRegistered(id);
  }

  private enum KeyState {
    waiting, pressed, released, hold
  }

  private final SingleAlarm myUpdateHeadersAlarm = new SingleAlarm(() -> updateToolWindowHeaders(), 50, this);
  private final CommandProcessor myCommandProcessor = new CommandProcessor();

  public ToolWindowManagerImpl(final Project project,
                               final WindowManagerEx windowManagerEx,
                               final ActionManager actionManager) {
    myProject = project;
    myWindowManager = windowManagerEx;

    if (project.isDefault()) {
      return;
    }

    MessageBusConnection busConnection = project.getMessageBus().connect(this);
    busConnection.subscribe(ToolWindowManagerListener.TOPIC, myDispatcher.getMulticaster());
    busConnection.subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
        if (myCurrentState != KeyState.hold) {
          resetHoldState();
        }
      }
    });
    busConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        if (project != myProject) {
          return;
        }

        //noinspection TestOnlyProblems
        init();

        PropertyChangeListener focusListener = it -> myUpdateHeadersAlarm.request();
        KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        keyboardFocusManager.addPropertyChangeListener("focusOwner", focusListener);
        Disposer.register(ToolWindowManagerImpl.this,
                          () -> keyboardFocusManager.removePropertyChangeListener("focusOwner", focusListener));
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        if (project == myProject) {
          ToolWindowManagerImpl.this.projectClosed();
        }
      }
    });

    myLayout.copyFrom(windowManagerEx.getLayout());

    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        getFocusManagerImpl(myProject).doWhenFocusSettlesDown(new ExpirableRunnable.ForProject(myProject) {
          @Override
          public void run() {
            if (!hasOpenEditorFiles()) {
              focusToolWindowByDefault(null);
            }
          }
        });
      }
    });

    Predicate<AWTEvent> predicate = event ->
      event.getID() == FocusEvent.FOCUS_LOST
      || event.getID() == FocusEvent.FOCUS_GAINED
      || event.getID() == MouseEvent.MOUSE_PRESSED
      || event.getID() == KeyEvent.KEY_PRESSED;

    Windows.ToolWindowFilter
      .filterBySignal(new Windows.Signal(predicate))
      .withEscAction(actionManager)
      .handleDocked(toolWindowId -> {})
      .handleFloating(toolWindowId -> {})
      .handleFocusLostOnPinned(toolWindowId -> {
        List<FinalizableCommand> commands = new ArrayList<>();
        deactivateToolWindowImpl(getRegisteredInfoOrLogError(toolWindowId), true, commands);
        myCommandProcessor.execute(commands, myProject.getDisposed());
      })
      .handleWindowed(toolWindowId -> {})
      .bind(myProject);
  }

  private void focusDefaultElementInSelectedEditor() {
    EditorsSplitters splittersToFocus = getSplittersToFocus();
    if (splittersToFocus != null) {
    final EditorWindow window = splittersToFocus.getCurrentWindow();
      if (window != null) {
        final EditorWithProviderComposite editor = window.getSelectedEditor();
        if (editor != null) {
          JComponent defaultFocusedComponentInEditor = editor.getPreferredFocusedComponent();
          if (defaultFocusedComponentInEditor != null) {
            defaultFocusedComponentInEditor.requestFocus();
          }
        }
      }
    }
  }

  private void updateToolWindowHeaders() {
    getFocusManager().doWhenFocusSettlesDown(new ExpirableRunnable.ForProject(myProject) {
      @Override
      public void run() {
        for (WindowInfoImpl each : myLayout.getInfos()) {
          if (each.isVisible()) {
            ToolWindow tw = getToolWindow(each.getId());
            if (tw instanceof ToolWindowImpl) {
              InternalDecorator decorator = ((ToolWindowImpl)tw).getDecorator();
              if (decorator != null) {
                decorator.repaint();
              }
            }
          }
        }
      }
    });
  }

  public boolean dispatchKeyEvent(@NotNull KeyEvent e) {
    if (e.getKeyCode() != KeyEvent.VK_CONTROL &&
        e.getKeyCode() != KeyEvent.VK_ALT &&
        e.getKeyCode() != KeyEvent.VK_SHIFT &&
        e.getKeyCode() != KeyEvent.VK_META) {
      if (e.getModifiers() == 0) {
        resetHoldState();
      }
      return false;
    }
    if (e.getID() != KeyEvent.KEY_PRESSED && e.getID() != KeyEvent.KEY_RELEASED) return false;

    Component parent = UIUtil.findUltimateParent(e.getComponent());
    if (parent instanceof IdeFrame) {
      if (((IdeFrame)parent).getProject() != myProject) {
        resetHoldState();
        return false;
      }
    }

    int vks = getActivateToolWindowVKsMask();

    if (vks == 0) {
      resetHoldState();
      return false;
    }

    int mouseMask = InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK;
    if (BitUtil.isSet(vks, keyCodeToInputMask(e.getKeyCode())) && (e.getModifiersEx() & mouseMask) == 0) {
      boolean pressed = e.getID() == KeyEvent.KEY_PRESSED;
      int modifiers = e.getModifiers();
      if (areAllModifiersPressed(modifiers, vks) || !pressed) {
        processState(pressed);
      }
      else {
        resetHoldState();
      }
    }

    return false;
  }

  private static boolean areAllModifiersPressed(@JdkConstants.InputEventMask int modifiers, @JdkConstants.InputEventMask int mask) {
    return (modifiers ^ mask) == 0;
  }

  @JdkConstants.InputEventMask
  private static int keyCodeToInputMask(int code) {
    int mask = 0;
    if (code == KeyEvent.VK_SHIFT) {
      mask = InputEvent.SHIFT_MASK;
    }
    if (code == KeyEvent.VK_CONTROL) {
      mask = InputEvent.CTRL_MASK;
    }
    if (code == KeyEvent.VK_META) {
      mask = InputEvent.META_MASK;
    }
    if (code == KeyEvent.VK_ALT) {
      mask = InputEvent.ALT_MASK;
    }
    return mask;
  }

  @JdkConstants.InputEventMask
  private static int getActivateToolWindowVKsMask() {
    if (ApplicationManager.getApplication() == null) return 0;

    Keymap keymap = Objects.requireNonNull(KeymapManager.getInstance()).getActiveKeymap();
    Shortcut[] baseShortcut = keymap.getShortcuts("ActivateProjectToolWindow");
    int baseModifiers = SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.ALT_MASK;
    for (Shortcut each : baseShortcut) {
      if (each instanceof KeyboardShortcut) {
        KeyStroke keyStroke = ((KeyboardShortcut)each).getFirstKeyStroke();
        baseModifiers = keyStroke.getModifiers();
        if (baseModifiers > 0) {
          break;
        }
      }
    }
    // We should filter out 'mixed' mask like InputEvent.META_MASK | InputEvent.META_DOWN_MASK
    return baseModifiers & (InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK | InputEvent.META_MASK | InputEvent.ALT_MASK);
  }

  private void resetHoldState() {
    myCurrentState = KeyState.waiting;
    processHoldState();
  }

  private void processState(boolean pressed) {
    if (pressed) {
      if (myCurrentState == KeyState.waiting) {
        myCurrentState = KeyState.pressed;
      }
      else if (myCurrentState == KeyState.released) {
        myCurrentState = KeyState.hold;
        processHoldState();
      }
    }
    else {
      if (myCurrentState == KeyState.pressed) {
        myCurrentState = KeyState.released;
        restartWaitingForSecondPressAlarm();
      }
      else {
        resetHoldState();
      }
    }
  }

  private void processHoldState() {
    if (myToolWindowsPane != null) {
      myToolWindowsPane.setStripesOverlayed(myCurrentState == KeyState.hold);
    }
  }

  private void restartWaitingForSecondPressAlarm() {
    myWaiterForSecondPress.cancelAllRequests();
    myWaiterForSecondPress.addRequest(mySecondPressRunnable, Registry.intValue("actionSystem.keyGestureDblClickTime"));
  }

  private boolean hasOpenEditorFiles() {
    return FileEditorManager.getInstance(myProject).getOpenFiles().length > 0;
  }

  private static IdeFocusManager getFocusManagerImpl(Project project) {
    return IdeFocusManager.getInstance(project);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public void dispose() {
    LOG.assertTrue(myId2StripeButton.isEmpty(), myId2StripeButton);
  }

  @TestOnly
  public void init() {
    if (myToolWindowsPane != null) {
      return;
    }

    myFrame = myWindowManager.allocateFrame(myProject);
    LOG.assertTrue(myFrame != null);

    myToolWindowsPane = new ToolWindowsPane(myFrame, this);
    Disposer.register(this, myToolWindowsPane);
    ((IdeRootPane)myFrame.getRootPane()).setToolWindowsPane(myToolWindowsPane);
    myFrame.setTitle(FrameTitleBuilder.getInstance().getProjectTitle(myProject));
    ((IdeRootPane)myFrame.getRootPane()).updateToolbar();

    IdeEventQueue.getInstance().addDispatcher(e -> {
      if (e instanceof KeyEvent) {
        dispatchKeyEvent((KeyEvent)e);
      }
      if (e instanceof WindowEvent && e.getID() == WindowEvent.WINDOW_LOST_FOCUS && e.getSource() == myFrame) {
        resetHoldState();
      }
      return false;
    }, this);

    UIUtil.putClientProperty(
      myToolWindowsPane, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, (Iterable<JComponent>)() -> {
        List<WindowInfoImpl> infos = myLayout.getInfos();
        List<JComponent> result = new ArrayList<>(infos.size());
        for (WindowInfoImpl info : infos) {
          @SuppressWarnings("ConstantConditions")
          JComponent decorator = getInternalDecorator(info.getId());
          if (decorator.getParent() == null) {
            result.add(decorator);
          }
        }
        return result.iterator();
      });
  }

  private void initAll(List<? super FinalizableCommand> commandsList) {
    appendUpdateToolWindowsPaneCmd(commandsList);

    JComponent editorComponent = createEditorComponent(myProject);
    editorComponent.setFocusable(false);

    appendSetEditorComponentCmd(editorComponent, commandsList);
  }

  private static JComponent createEditorComponent(@NotNull Project project) {
    return FrameEditorComponentProvider.EP.getExtensions()[0].createEditorComponent(project);
  }

  private void registerToolWindowsFromBeans(List<? super FinalizableCommand> list) {
    for (ToolWindowEP bean : ToolWindowEP.EP_NAME.getExtensionList()) {
      list.add(new FinalizableCommand(EmptyRunnable.INSTANCE) {
        @Override
        public void run() {
          try {
            initToolWindow(bean);
          }
          catch (ProcessCanceledException e) { throw e; }
          catch (Throwable t) {
            LOG.error("failed to init toolwindow " + bean.factoryClass, t);
          }
        }
      });
    }
  }

  @Override
  public void initToolWindow(@NotNull ToolWindowEP bean) {
    Condition<Project> condition = bean.getCondition();
    if (condition != null && !condition.value(myProject)) return;

    WindowInfoImpl before = myLayout.getInfo(bean.id, false);
    boolean visible = before != null && before.isVisible();
    JLabel label = createInitializingLabel();
    ToolWindowAnchor toolWindowAnchor = ToolWindowAnchor.fromText(bean.anchor);
    final ToolWindowFactory factory = bean.getToolWindowFactory();
    ToolWindow window = registerToolWindow(bean.id, label, toolWindowAnchor, false, bean.canCloseContents,
                                           DumbService.isDumbAware(factory), factory.shouldBeAvailable(myProject));
    final ToolWindowImpl toolWindow = (ToolWindowImpl)window;
    toolWindow.setContentFactory(factory);
    if (bean.icon != null && toolWindow.getIcon() == null) {
      Icon icon = IconLoader.findIcon(bean.icon, factory.getClass());
      if (icon == null) {
        try {
          icon = IconLoader.getIcon(bean.icon);
        }
        catch (Exception ignored) {
        }
      }
      toolWindow.setIcon(icon);
    }

    WindowInfoImpl info = getRegisteredInfoOrLogError(bean.id);
    if (!info.isSplit() && bean.secondary && !info.isWasRead()) {
      toolWindow.setSplitMode(true, null);
    }

    final DumbAwareRunnable runnable = () -> {
      if (toolWindow.isDisposed()) return;

      toolWindow.ensureContentInitialized();
    };
    if (visible) {
      runnable.run();
    }
    else {
      UiNotifyConnector.doWhenFirstShown(label, () -> ApplicationManager.getApplication().invokeLater(runnable));
    }
  }

  @NotNull
  private static JLabel createInitializingLabel() {
    JLabel label = new JLabel("Initializing...", SwingConstants.CENTER);
    label.setOpaque(true);
    final Color treeBg = UIManager.getColor("Tree.background");
    label.setBackground(ColorUtil.toAlpha(treeBg, 180));
    final Color treeFg = UIUtil.getTreeForeground();
    label.setForeground(ColorUtil.toAlpha(treeFg, 180));
    return label;
  }

  public void projectClosed() {
    if (myFrame == null) {
      return;
    }

    // remove ToolWindowsPane
    ((IdeRootPane)myFrame.getRootPane()).setToolWindowsPane(null);
    myWindowManager.releaseFrame(myFrame);
    List<FinalizableCommand> commandsList = new ArrayList<>();
    appendUpdateToolWindowsPaneCmd(commandsList);

    // Hide all tool windows

    for (WindowInfoImpl info : myLayout.getInfos()) {
      deactivateToolWindowImpl(info, true, commandsList);
    }

    appendSetEditorComponentCmd(null, commandsList);
    execute(commandsList);
    myFrame = null;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void addToolWindowManagerListener(@NotNull ToolWindowManagerListener listener) {
    myDispatcher.addListener(listener);
  }

  @SuppressWarnings("deprecation")
  @Override
  public void addToolWindowManagerListener(@NotNull ToolWindowManagerListener listener, @NotNull Disposable parentDisposable) {
    myProject.getMessageBus().connect(parentDisposable).subscribe(ToolWindowManagerListener.TOPIC, listener);
  }

  @SuppressWarnings("deprecation")
  @Override
  public void removeToolWindowManagerListener(@NotNull ToolWindowManagerListener listener) {
    myDispatcher.removeListener(listener);
  }

  void execute(@NotNull List<FinalizableCommand> commandList) {
    execute(commandList, true);
  }

  /**
   * This is helper method. It delegated its functionality to the WindowManager.
   * Before delegating it fires state changed.
   */
  private void execute(@NotNull List<FinalizableCommand> commandList, boolean isFireStateChangedEvent) {
    if (isFireStateChangedEvent) {
      for (FinalizableCommand each : commandList) {
        if (each.willChangeState()) {
          fireStateChanged();
          break;
        }
      }
    }

    for (FinalizableCommand each : commandList) {
      each.beforeExecute(this);
    }
    myCommandProcessor.execute(commandList, myProject.getDisposed());
  }

  private void flushCommands() {
    myCommandProcessor.flush();
  }

  @Override
  public void activateEditorComponent() {
    focusDefaultElementInSelectedEditor();
  }

  private void deactivateWindows(@NotNull String idToIgnore, @NotNull List<? super FinalizableCommand> commandList) {
    for (WindowInfoImpl info : myLayout.getInfos()) {
      if (!idToIgnore.equals(info.getId())) {
        deactivateToolWindowImpl(info, isToHideOnDeactivation(info), commandList);
      }
    }
  }

  private static boolean isToHideOnDeactivation(@NotNull final WindowInfoImpl info) {
    if (info.isFloating() || info.isWindowed()) return false;
    return info.isAutoHide() || info.isSliding();
  }

  /**
   * Helper method. It makes window visible, activates it and request focus into the tool window.
   * But it doesn't deactivate other tool windows. Use {@code prepareForActivation} method to
   * deactivates other tool windows.
   *
   * @param dirtyMode if {@code true} then all UI operations are performed in "dirty" mode.
   *                  It means that UI isn't validated and repainted just after each add/remove operation.
   */
  private void showAndActivate(@NotNull String id,
                               final boolean dirtyMode,
                               @NotNull List<? super FinalizableCommand> commandsList,
                               boolean autoFocusContents) {
    //noinspection ConstantConditions
    if (!getToolWindow(id).isAvailable()) {
      return;
    }

    // show activated
    final WindowInfoImpl info = getRegisteredInfoOrLogError(id);
    boolean toApplyInfo = false;
    if (!info.isActive()) {
      info.setActive(true);
      toApplyInfo = true;
    }
    showToolWindowImpl(id, dirtyMode, commandsList);

    // activate
    if (toApplyInfo) {
      appendApplyWindowInfoCmd(info, commandsList);
      myActiveStack.push(id);
    }

    if (autoFocusContents && ApplicationManager.getApplication().isActive()) {
      appendRequestFocusInToolWindowCmd(id, commandsList);
    }
  }

  void activateToolWindow(@NotNull String id, boolean forced, boolean autoFocusContents) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: activateToolWindow(" + id + ")");
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkId(id);

    List<FinalizableCommand> commandList = new ArrayList<>();
    activateToolWindowImpl(id, commandList, forced, autoFocusContents);
    execute(commandList);
  }

  private void activateToolWindowImpl(@NotNull String id,
                                      @NotNull List<? super FinalizableCommand> commandList,
                                      boolean forced,
                                      boolean autoFocusContents) {
    ToolWindowCollector.getInstance().recordActivation(id);
    autoFocusContents &= forced;

    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: activateToolWindowImpl(" + id + ")");
    }

    //noinspection ConstantConditions
    if (!getToolWindow(id).isAvailable()) {
      // Tool window can be "logically" active but not focused. For example,
      // when the user switched to another application. So we just need to bring
      // tool window's window to front.
      final InternalDecorator decorator = getInternalDecorator(id);
      if (!decorator.hasFocus() && autoFocusContents) {
        appendRequestFocusInToolWindowCmd(id, commandList);
      }
      return;
    }
    deactivateWindows(id, commandList);
    showAndActivate(id, false, commandList, autoFocusContents);
  }

  /**
   * Checks whether the specified {@code id} defines installed tool
   * window. If it's not then throws {@code IllegalStateException}.
   *
   * @throws IllegalStateException if tool window isn't installed.
   */
  private void checkId(@NotNull String id) {
    getRegisteredInfoOrLogError(id);
  }

  @NotNull
  private WindowInfoImpl getRegisteredInfoOrLogError(@NotNull String id) {
    WindowInfoImpl info = myLayout.getInfo(id, false);
    if (info == null) {
      throw new IllegalThreadStateException("window with id=\"" + id + "\" is unknown");
    }
    if (!info.isRegistered()) {
      LOG.error("window with id=\"" + id + "\" isn't registered");
    }
    return info;
  }

  /**
   * Helper method. It deactivates (and hides) window with specified {@code id}.
   */
  private void deactivateToolWindowImpl(@NotNull WindowInfoImpl info, final boolean shouldHide, @NotNull List<? super FinalizableCommand> commandsList) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: deactivateToolWindowImpl(" + info.getId() + "," + shouldHide + ")");
    }

    if (shouldHide && info.isVisible()) {
      //noinspection ConstantConditions
      applyInfo(info.getId(), info, commandsList);
    }
    info.setActive(false);
    appendApplyWindowInfoCmd(info, commandsList);
  }

  @NotNull
  @Override
  public String[] getToolWindowIds() {
    final List<WindowInfoImpl> infos = myLayout.getInfos();
    final String[] ids = ArrayUtil.newStringArray(infos.size());
    for (int i = 0; i < infos.size(); i++) {
      ids[i] = infos.get(i).getId();
    }
    return ids;
  }

  @Override
  public String getActiveToolWindowId() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myLayout.getActiveId();
  }

  @Override
  public String getLastActiveToolWindowId() {
    return getLastActiveToolWindowId(null);
  }

  @Override
  @Nullable
  public String getLastActiveToolWindowId(@Nullable Condition<? super JComponent> condition) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    String lastActiveToolWindowId = null;
    for (int i = 0; i < myActiveStack.getPersistentSize(); i++) {
      final String id = myActiveStack.peekPersistent(i);
      final ToolWindow toolWindow = getToolWindow(id);
      LOG.assertTrue(toolWindow != null);
      if (toolWindow.isAvailable()) {
        if (condition == null || condition.value(toolWindow.getComponent())) {
          lastActiveToolWindowId = id;
          break;
        }
      }
    }
    return lastActiveToolWindowId;
  }

  /**
   * @return floating decorator for the tool window with specified {@code ID}.
   */
  private FloatingDecorator getFloatingDecorator(@NotNull String id) {
    return myId2FloatingDecorator.get(id);
  }
  /**
   * @return windowed decorator for the tool window with specified {@code ID}.
   */
  private WindowedDecorator getWindowedDecorator(@NotNull String id) {
    return myId2WindowedDecorator.get(id);
  }
  /**
   * @return internal decorator for the tool window with specified {@code ID}.
   */
  private InternalDecorator getInternalDecorator(@NotNull String id) {
    return myId2InternalDecorator.get(id);
  }

  /**
   * @return tool button for the window with specified {@code ID}.
   */
  StripeButton getStripeButton(@NotNull String id) {
    return myId2StripeButton.get(id);
  }

  @Override
  @NotNull
  public List<String> getIdsOn(@NotNull final ToolWindowAnchor anchor) {
    return myLayout.getVisibleIdsOn(anchor, this);
  }

  @Override
  @Nullable
  // cannot be ToolWindowEx because of backward compatibility
  public ToolWindow getToolWindow(@Nullable String id) {
    if (id == null || !myLayout.isToolWindowRegistered(id)) {
      return null;
    }

    InternalDecorator decorator = getInternalDecorator(id);
    return decorator != null ? decorator.getToolWindow() : null;
  }

  void showToolWindow(@NotNull String id) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: showToolWindow(" + id + ")");
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<FinalizableCommand> commandList = new ArrayList<>();
    showToolWindowImpl(id, false, commandList);
    execute(commandList);
  }

  @Override
  public void hideToolWindow(@NotNull final String id, final boolean hideSide) {
    hideToolWindow(id, hideSide, true);
  }

  public void hideToolWindow(@NotNull String id, final boolean hideSide, final boolean moveFocus) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final WindowInfoImpl info = getRegisteredInfoOrLogError(id);
    if (!info.isVisible()) {
      return;
    }

    List<FinalizableCommand> commandList = new ArrayList<>();
    final boolean wasActive = info.isActive();

    // hide and deactivate

    deactivateToolWindowImpl(info, true, commandList);

    if (hideSide && !info.isFloating() && !info.isWindowed()) {
      final List<String> ids = myLayout.getVisibleIdsOn(info.getAnchor(), this);
      for (String each : ids) {
        myActiveStack.remove(each, true);
      }

      while (!mySideStack.isEmpty(info.getAnchor())) {
        mySideStack.pop(info.getAnchor());
      }

      for (WindowInfoImpl eachInfo : myLayout.getInfos()) {
        if (eachInfo.isVisible() && eachInfo.getAnchor() == info.getAnchor()) {
          deactivateToolWindowImpl(eachInfo, true, commandList);
        }
      }
    }
    else if (isStackEnabled()) {
      // first of all we have to find tool window that was located at the same side and was hidden

      WindowInfoImpl info2 = null;
      while (!mySideStack.isEmpty(info.getAnchor())) {
        final WindowInfoImpl storedInfo = mySideStack.pop(info.getAnchor());
        if (storedInfo.isSplit() != info.isSplit()) {
          continue;
        }

        final WindowInfoImpl currentInfo = getRegisteredInfoOrLogError(Objects.requireNonNull(storedInfo.getId()));
        // SideStack contains copies of real WindowInfos. It means that
        // these stored infos can be invalid. The following loop removes invalid WindowInfos.
        if (storedInfo.getAnchor() == currentInfo.getAnchor() &&
            storedInfo.getType() == currentInfo.getType() &&
            storedInfo.isAutoHide() == currentInfo.isAutoHide()) {
          info2 = storedInfo;
          break;
        }
      }
      if (info2 != null) {
        showToolWindowImpl(Objects.requireNonNull(info2.getId()), false, commandList);
      }

      // If we hide currently active tool window then we should activate the previous
      // one which is located in the tool window stack.
      // Activate another tool window if no active tool window exists and
      // window stack is enabled.

      myActiveStack.remove(id, false); // hidden window should be at the top of stack

      if (wasActive && moveFocus && !myActiveStack.isEmpty()) {
        final String toBeActivatedId = myActiveStack.pop();
        if (getRegisteredInfoOrLogError(toBeActivatedId).isVisible() || isStackEnabled()) {
          activateToolWindowImpl(toBeActivatedId, commandList, false, true);
        }
        else {
          focusToolWindowByDefault(id);
        }
      }
    }

    execute(commandList);
  }

  private static boolean isStackEnabled() {
    return Registry.is("ide.enable.toolwindow.stack");
  }

  /**
   * @param dirtyMode if {@code true} then all UI operations are performed in dirty mode.
   */
  private void showToolWindowImpl(@NotNull String id, final boolean dirtyMode, @NotNull List<? super FinalizableCommand> commandsList) {
    final WindowInfoImpl toBeShownInfo = getRegisteredInfoOrLogError(id);
    ToolWindow window = getToolWindow(id);
    if (window != null && toBeShownInfo.isWindowed()) {
      UIUtil.toFront(UIUtil.getWindow(window.getComponent()));
    }
    if (toBeShownInfo.isVisible() || window == null || !window.isAvailable()) {
      return;
    }

    toBeShownInfo.setVisible(true);
    final InternalDecorator decorator = getInternalDecorator(id);

    if (toBeShownInfo.isFloating()) {
      commandsList.add(new AddFloatingDecoratorCmd(decorator, toBeShownInfo));
    }
    else if (toBeShownInfo.isWindowed()) {
      commandsList.add(new AddWindowedDecoratorCmd(decorator, toBeShownInfo));
    }
    else { // docked and sliding windows

      // If there is tool window on the same side then we have to hide it, i.e.
      // clear place for tool window to be shown.
      //
      // We store WindowInfo of hidden tool window in the SideStack (if the tool window
      // is docked and not auto-hide one). Therefore it's possible to restore the
      // hidden tool window when showing tool window will be closed.

      for (final WindowInfoImpl info : myLayout.getInfos()) {
        if (id.equals(info.getId())) {
          continue;
        }
        if (info.isVisible() &&
            info.getType() == toBeShownInfo.getType() &&
            info.getAnchor() == toBeShownInfo.getAnchor() &&
            info.isSplit() == toBeShownInfo.isSplit()) {
          // hide and deactivate tool window
          info.setVisible(false);
          appendRemoveDecoratorCmd(Objects.requireNonNull(info.getId()), false, commandsList);
          if (info.isActive()) {
            info.setActive(false);
          }
          appendApplyWindowInfoCmd(info, commandsList);
          // store WindowInfo into the SideStack
          if (info.isDocked() && !info.isAutoHide()) {
            mySideStack.push(info);
          }
        }
      }
      appendAddDecoratorCmd(decorator, toBeShownInfo, dirtyMode, commandsList);

      // Remove tool window from the SideStack.

      mySideStack.remove(id);
    }

    if (!toBeShownInfo.isShowStripeButton()) {
      toBeShownInfo.setShowStripeButton(true);
    }

    appendApplyWindowInfoCmd(toBeShownInfo, commandsList);
  }

  @NotNull
  @Override
  public ToolWindow registerToolWindow(@NotNull final String id,
                                       @NotNull final JComponent component,
                                       @NotNull final ToolWindowAnchor anchor) {
    return registerToolWindow(id, component, anchor, false, false, false, true);
  }

  @NotNull
  @Override
  public ToolWindow registerToolWindow(@NotNull final String id,
                                       @NotNull JComponent component,
                                       @NotNull ToolWindowAnchor anchor,
                                       @NotNull Disposable parentDisposable) {
    return registerToolWindow(id, component, anchor, parentDisposable, false, false);
  }

  @NotNull
  @Override
  public ToolWindow registerToolWindow(@NotNull String id,
                                       @NotNull JComponent component,
                                       @NotNull ToolWindowAnchor anchor,
                                       @NotNull Disposable parentDisposable,
                                       boolean canWorkInDumbMode) {
    return registerToolWindow(id, component, anchor, parentDisposable, canWorkInDumbMode, false);
  }

  @NotNull
  @Override
  public ToolWindow registerToolWindow(@NotNull final String id,
                                       @NotNull JComponent component,
                                       @NotNull ToolWindowAnchor anchor,
                                       @NotNull Disposable parentDisposable,
                                       boolean canWorkInDumbMode, boolean canCloseContents) {
    return registerToolWindow(id, component, anchor, false, canCloseContents, canWorkInDumbMode, true);
  }

  @NotNull
  @Override
  public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor) {
    return registerToolWindow(id, null, anchor, false, canCloseContent, false, true);
  }

  @NotNull
  @Override
  public ToolWindow registerToolWindow(@NotNull final String id,
                                       final boolean canCloseContent,
                                       @NotNull final ToolWindowAnchor anchor,
                                       final boolean secondary) {
    return registerToolWindow(id, null, anchor, secondary, canCloseContent, false, true);
  }


  @NotNull
  @Override
  public ToolWindow registerToolWindow(@NotNull final String id,
                                       final boolean canCloseContent,
                                       @NotNull final ToolWindowAnchor anchor,
                                       @NotNull final Disposable parentDisposable,
                                       final boolean canWorkInDumbMode) {
    return registerToolWindow(id, canCloseContent, anchor, parentDisposable, canWorkInDumbMode, false);
  }

  @NotNull
  @Override
  public ToolWindow registerToolWindow(@NotNull String id,
                                       boolean canCloseContent,
                                       @NotNull ToolWindowAnchor anchor,
                                       @NotNull Disposable parentDisposable,
                                       boolean canWorkInDumbMode,
                                       boolean secondary) {
    return registerToolWindow(id, null, anchor, secondary, canCloseContent, canWorkInDumbMode, true);
  }

  @NotNull
  private ToolWindow registerToolWindow(@NotNull final String id,
                                        @Nullable final JComponent component,
                                        @NotNull final ToolWindowAnchor anchor,
                                        boolean sideTool,
                                        boolean canCloseContent,
                                        final boolean canWorkInDumbMode,
                                        boolean shouldBeAvailable) {
    //noinspection TestOnlyProblems
    init();

    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: installToolWindow(" + id + "," + component + "," + anchor + "\")");
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    WindowInfoImpl existingInfo = myLayout.getInfo(id, false);
    if (existingInfo != null && existingInfo.isRegistered()) {
      throw new IllegalArgumentException("window with id=\"" + id + "\" is already registered");
    }

    final WindowInfoImpl info = myLayout.register(id, anchor, sideTool);
    final boolean wasActive = info.isActive();
    final boolean wasVisible = info.isVisible();
    info.setActive(false);
    info.setVisible(false);
    // Create decorator

    ToolWindowImpl toolWindow = new ToolWindowImpl(this, id, canCloseContent, component);
    Disposer.register(this, toolWindow);
    toolWindow.setAvailable(shouldBeAvailable, null);
    InternalDecorator decorator = new InternalDecorator(myProject, info.copy(), toolWindow, canWorkInDumbMode);
    ActivateToolWindowAction.ensureToolWindowActionRegistered(toolWindow);
    myId2InternalDecorator.put(id, decorator);
    decorator.addInternalDecoratorListener(myInternalDecoratorListener);
    toolWindow.addPropertyChangeListener(myToolWindowPropertyChangeListener);
    myId2FocusWatcher.put(id, new ToolWindowFocusWatcher(toolWindow));

    // Create and show tool button

    final StripeButton button = new StripeButton(decorator, myToolWindowsPane);
    Disposer.register(toolWindow, button);
    myId2StripeButton.put(id, button);
    List<FinalizableCommand> commandsList = new ArrayList<>();
    appendAddButtonCmd(button, info, commandsList);

    // If preloaded info is visible or active then we have to show/activate the installed
    // tool window. This step has sense only for windows which are not in the auto hide
    // mode. But if tool window was active but its mode doesn't allow to activate it again
    // (for example, tool window is in auto hide mode) then we just activate editor component.

    if (!info.isAutoHide() && (info.isDocked() || info.isFloating())) {
      if (wasActive) {
        activateToolWindowImpl(Objects.requireNonNull(info.getId()), commandsList, true, true);
      }
      else if (wasVisible) {
        showToolWindowImpl(Objects.requireNonNull(info.getId()), false, commandsList);
      }
    }

    execute(commandsList);
    fireToolWindowRegistered(id);
    return toolWindow;
  }

  @Override
  public void unregisterToolWindow(@NotNull final String id) {
    ToolWindowImpl window = (ToolWindowImpl)getToolWindow(id);
    if (window != null) {
      Disposer.dispose(window);
    }
  }

  void doUnregisterToolWindow(@NotNull String id) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: unregisterToolWindow(" + id + ")");
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    WindowInfoImpl info = myLayout.getInfo(id, false);
    if (info == null || !info.isRegistered()) {
      return;
    }

    final ToolWindowEx toolWindow = (ToolWindowEx)getToolWindow(id);

    // Remove decorator and tool button from the screen
    List<FinalizableCommand> commandsList = new ArrayList<>();
    if (info.isVisible()) {
      applyInfo(id, info, commandsList);
    }

    // Save recent appearance of tool window
    myLayout.unregister(id);
    myActiveStack.remove(id, true);
    mySideStack.remove(id);
    appendRemoveButtonCmd(id, info, commandsList);
    appendApplyWindowInfoCmd(info, commandsList);

    execute(commandsList, false);
    assert toolWindow != null;
    myProject.getMessageBus().syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowUnregistered(id, toolWindow);

    // Remove all references on tool window and save its last properties
    toolWindow.removePropertyChangeListener(myToolWindowPropertyChangeListener);

    // Destroy stripe button
    myId2StripeButton.remove(id);
    //
    ToolWindowFocusWatcher watcher = (ToolWindowFocusWatcher)myId2FocusWatcher.remove(id);
    watcher.deinstall();

    // Destroy decorator
    final InternalDecorator decorator = getInternalDecorator(id);
    decorator.dispose();
    decorator.removeInternalDecoratorListener(myInternalDecoratorListener);
    myId2InternalDecorator.remove(id);
  }

  private void applyInfo(@NotNull String id, WindowInfoImpl info, List<? super FinalizableCommand> commandsList) {
    info.setVisible(false);
    if (info.isFloating()) {
      appendRemoveFloatingDecoratorCmd(info, commandsList);
    }
    else  if (info.isWindowed()) {
       appendRemoveWindowedDecoratorCmd(info, commandsList);
     }
    else { // floating and sliding windows
      appendRemoveDecoratorCmd(id, false, commandsList);
    }
  }

  @NotNull
  @Override
  public DesktopLayout getLayout() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myLayout;
  }

  @Override
  public void setLayoutToRestoreLater(DesktopLayout layout) {
    myLayoutToRestoreLater = layout;
  }

  @Override
  public DesktopLayout getLayoutToRestoreLater() {
    return myLayoutToRestoreLater;
  }

  @Override
  public void setLayout(@NotNull final DesktopLayout layout) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<FinalizableCommand> commandList = new ArrayList<>();
    // hide tool window that are invisible or its info is not presented in new layout
    final List<WindowInfoImpl> currentInfos = myLayout.getInfos();
    for (final WindowInfoImpl currentInfo : currentInfos) {
      final WindowInfoImpl info = layout.getInfo(Objects.requireNonNull(currentInfo.getId()), false);
      if (currentInfo.isVisible() && (info == null || !info.isVisible())) {
        deactivateToolWindowImpl(currentInfo, true, commandList);
      }
    }
    // change anchor of tool windows
    for (final WindowInfoImpl currentInfo : currentInfos) {
      final WindowInfoImpl info = layout.getInfo(Objects.requireNonNull(currentInfo.getId()), false);
      if (info == null) {
        continue;
      }
      if (currentInfo.getAnchor() != info.getAnchor() || currentInfo.getOrder() != info.getOrder()) {
        setToolWindowAnchorImpl(currentInfo.getId(), info.getAnchor(), info.getOrder(), commandList);
      }
    }
    // change types of tool windows
    for (final WindowInfoImpl currentInfo : currentInfos) {
      final WindowInfoImpl info = layout.getInfo(Objects.requireNonNull(currentInfo.getId()), false);
      if (info == null) {
        continue;
      }
      if (currentInfo.getType() != info.getType()) {
        setToolWindowTypeImpl(currentInfo.getId(), info.getType(), commandList);
      }
    }
    // change auto-hide state
    for (final WindowInfoImpl currentInfo : currentInfos) {
      final WindowInfoImpl info = layout.getInfo(Objects.requireNonNull(currentInfo.getId()), false);
      if (info == null) {
        continue;
      }
      if (currentInfo.isAutoHide() != info.isAutoHide()) {
        setToolWindowAutoHideImpl(currentInfo.getId(), info.isAutoHide(), commandList);
      }
    }
    // restore visibility
    for (final WindowInfoImpl currentInfo : currentInfos) {
      final WindowInfoImpl info = layout.getInfo(Objects.requireNonNull(currentInfo.getId()), false);
      if (info == null) {
        continue;
      }
      if (info.isVisible()) {
        showToolWindowImpl(currentInfo.getId(), false, commandList);
      }
    }

    execute(commandList);
  }

  @Override
  public void invokeLater(@NotNull final Runnable runnable) {
    List<FinalizableCommand> commandList = new ArrayList<>();
    commandList.add(new InvokeLaterCmd(runnable, myCommandProcessor));
    execute(commandList);
  }

  @NotNull
  @Override
  public IdeFocusManager getFocusManager() {
    return IdeFocusManager.getInstance(myProject);
  }

  @Override
  public boolean canShowNotification(@NotNull final String toolWindowId) {
    if (!myLayout.isToolWindowRegistered(toolWindowId)) {
      return false;
    }
    final Stripe stripe = myToolWindowsPane.getStripeFor(toolWindowId);
    return stripe != null && stripe.getButtonFor(toolWindowId) != null;
  }

  @Override
  public void notifyByBalloon(@NotNull final String toolWindowId, @NotNull final MessageType type, @NotNull final String htmlBody) {
    //noinspection SSBasedInspection
    notifyByBalloon(toolWindowId, type, htmlBody, null, null);
  }

  @Override
  public void notifyByBalloon(@NotNull final String toolWindowId,
                              @NotNull final MessageType type,
                              @NotNull final String text,
                              @Nullable final Icon icon,
                              @Nullable final HyperlinkListener listener) {
    checkId(toolWindowId);

    Balloon existing = myWindow2Balloon.get(toolWindowId);
    if (existing != null) {
      Disposer.dispose(existing);
    }

    final Stripe stripe = myToolWindowsPane.getStripeFor(toolWindowId);
    if (stripe == null) {
      return;
    }
    final ToolWindowImpl window = getInternalDecorator(toolWindowId).getToolWindow();
    if (!window.isAvailable()) {
      window.setPlaceholderMode(true);
      stripe.updatePresentation();
      stripe.revalidate();
      stripe.repaint();
    }

    final ToolWindowAnchor anchor = getRegisteredInfoOrLogError(toolWindowId).getAnchor();
    final Ref<Balloon.Position> position = Ref.create(Balloon.Position.below);
    if (ToolWindowAnchor.TOP == anchor) {
      position.set(Balloon.Position.below);
    }
    else if (ToolWindowAnchor.BOTTOM == anchor) {
      position.set(Balloon.Position.above);
    }
    else if (ToolWindowAnchor.LEFT == anchor) {
      position.set(Balloon.Position.atRight);
    }
    else if (ToolWindowAnchor.RIGHT == anchor) {
      position.set(Balloon.Position.atLeft);
    }

    final BalloonHyperlinkListener listenerWrapper = new BalloonHyperlinkListener(listener);
    final Balloon balloon = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(text.replace("\n", "<br>"), icon, type.getTitleForeground(), type.getPopupBackground(), listenerWrapper)
      .setBorderColor(type.getBorderColor()).setHideOnClickOutside(false).setHideOnFrameResize(false).createBalloon();
    NotificationsManagerImpl.frameActivateBalloonListener(balloon, () ->
      AppExecutorUtil.getAppScheduledExecutorService().schedule(()->((BalloonImpl)balloon).setHideOnClickOutside(true), 100, TimeUnit.MILLISECONDS)
    );
    listenerWrapper.myBalloon = balloon;
    myWindow2Balloon.put(toolWindowId, balloon);
    Disposer.register(balloon, () -> {
      window.setPlaceholderMode(false);
      stripe.updatePresentation();
      stripe.revalidate();
      stripe.repaint();
      myWindow2Balloon.remove(toolWindowId);
    });
    Disposer.register(getProject(), balloon);

    execute(new ArrayList<>(Collections.<FinalizableCommand>singletonList(new FinalizableCommand(null) {
      @Override
      public void run() {
        final StripeButton button = stripe.getButtonFor(toolWindowId);
        //noinspection ObjectToString
        LOG.assertTrue(button != null, "Button was not found, popup won't be shown. Toolwindow id: " +
                                       toolWindowId + ", message: " + text + ", message type: " + type);
        //noinspection ConstantConditions
        if (button == null) return;

        final Runnable show = () -> {
            PositionTracker<Balloon> tracker;
            if (button.isShowing()) {
              tracker = new PositionTracker<Balloon>(button) {
                @Override
                @Nullable
                public RelativePoint recalculateLocation(Balloon object) {
                  Stripe twStripe = myToolWindowsPane.getStripeFor(toolWindowId);
                  StripeButton twButton = twStripe != null ? twStripe.getButtonFor(toolWindowId) : null;
                  if (twButton == null) {
                    return null;
                  }
                  //noinspection ConstantConditions
                  if (getToolWindow(toolWindowId).getAnchor() != anchor) {
                    object.hide();
                    return null;
                  }
                  final Point point = new Point(twButton.getBounds().width / 2, twButton.getHeight() / 2 - 2);
                  return new RelativePoint(twButton, point);
                }
              };
            }
            else {
              tracker = new PositionTracker<Balloon>(myToolWindowsPane) {
                @Override
                public RelativePoint recalculateLocation(Balloon object) {
                  final Rectangle bounds = myToolWindowsPane.getBounds();
                  final Point target = UIUtil.getCenterPoint(bounds, new Dimension(1, 1));
                  if (ToolWindowAnchor.TOP == anchor) {
                    target.y = 0;
                  }
                  else if (ToolWindowAnchor.BOTTOM == anchor) {
                    target.y = bounds.height - 3;
                  }
                  else if (ToolWindowAnchor.LEFT == anchor) {
                    target.x = 0;
                  }
                  else if (ToolWindowAnchor.RIGHT == anchor) {
                    target.x = bounds.width;
                  }
                  return new RelativePoint(myToolWindowsPane, target);
                }
              };
            }
            if (!balloon.isDisposed()) {
              balloon.show(tracker, position.get());
            }
        };

        if (!button.isValid()) {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(show);
        }
        else {
          show.run();
        }
      }
    })));
  }

  @Override
  public Balloon getToolWindowBalloon(String id) {
    return myWindow2Balloon.get(id);
  }

  @Override
  public boolean isEditorComponentActive() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    Component owner = getFocusManager().getFocusOwner();
    EditorsSplitters splitters = UIUtil.getParentOfType(EditorsSplitters.class, owner);
    return splitters != null;
  }

  @NotNull
  ToolWindowAnchor getToolWindowAnchor(@NotNull String id) {
    return getRegisteredInfoOrLogError(id).getAnchor();
  }

  void setToolWindowAnchor(@NotNull String id, @NotNull ToolWindowAnchor anchor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    setToolWindowAnchor(id, anchor, -1);
  }

  private void setToolWindowAnchor(@NotNull String id, @NotNull ToolWindowAnchor anchor, final int order) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<FinalizableCommand> commandList = new ArrayList<>();
    setToolWindowAnchorImpl(id, anchor, order, commandList);
    execute(commandList);
  }

  private void setToolWindowAnchorImpl(@NotNull String id,
                                       @NotNull ToolWindowAnchor anchor,
                                       final int order,
                                       @NotNull List<? super FinalizableCommand> commandsList) {
    final WindowInfoImpl info = getRegisteredInfoOrLogError(id);
    if (anchor == info.getAnchor() && order == info.getOrder()) {
      return;
    }
    // if tool window isn't visible or only order number is changed then just remove/add stripe button
    if (!info.isVisible() || anchor == info.getAnchor() || info.isFloating() || info.isWindowed()) {
      appendRemoveButtonCmd(id, info, commandsList);
      myLayout.setAnchor(id, anchor, order);
      // update infos for all window. Actually we have to update only infos affected by
      // setAnchor method
      for (WindowInfoImpl info1 : myLayout.getInfos()) {
        appendApplyWindowInfoCmd(info1, commandsList);
      }
      appendAddButtonCmd(getStripeButton(id), info, commandsList);
    }
    else { // for docked and sliding windows we have to move buttons and window's decorators
      info.setVisible(false);
      appendRemoveDecoratorCmd(id, false, commandsList);
      appendRemoveButtonCmd(id, info, commandsList);
      myLayout.setAnchor(id, anchor, order);
      // update infos for all window. Actually we have to update only infos affected by
      // setAnchor method
      for (WindowInfoImpl info1 : myLayout.getInfos()) {
        appendApplyWindowInfoCmd(info1, commandsList);
      }
      appendAddButtonCmd(getStripeButton(id), info, commandsList);
      showToolWindowImpl(id, false, commandsList);
      if (info.isActive()) {
        appendRequestFocusInToolWindowCmd(id, commandsList);
      }
    }
  }

  boolean isSplitMode(@NotNull String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getRegisteredInfoOrLogError(id).isSplit();
  }

  @NotNull
  ToolWindowContentUiType getContentUiType(@NotNull String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getRegisteredInfoOrLogError(id).getContentUiType();
  }

  void setSideTool(@NotNull String id, boolean isSide) {
    List<FinalizableCommand> commandList = new ArrayList<>();
    setSplitModeImpl(id, isSide, commandList);
    execute(commandList);
  }

  void setContentUiType(@NotNull String id, @NotNull ToolWindowContentUiType type) {
    WindowInfoImpl info = getRegisteredInfoOrLogError(id);
    info.setContentUiType(type);
    List<FinalizableCommand> commandList = new ArrayList<>();
    appendApplyWindowInfoCmd(info, commandList);
    execute(commandList);
  }

  void setSideToolAndAnchor(@NotNull String id, @NotNull ToolWindowAnchor anchor, int order, boolean isSide) {
    hideToolWindow(id, false);
    myLayout.setSplitMode(id, isSide);
    setToolWindowAnchor(id, anchor, order);
    activateToolWindow(id, false, false);
  }

  private void setSplitModeImpl(@NotNull String id, final boolean isSplit, @NotNull List<? super FinalizableCommand> commandList) {
    final WindowInfoImpl info = getRegisteredInfoOrLogError(id);
    if (isSplit == info.isSplit()) {
      return;
    }

    myLayout.setSplitMode(id, isSplit);

    boolean wasActive = info.isActive();
    boolean wasVisible = info.isVisible();
    // We should hide the window and show it in a 'new place' to automatically hide possible window that is already located in a 'new place'
    if (wasActive || wasVisible) {
      hideToolWindow(id, false);
    }
    for (WindowInfoImpl info1 : myLayout.getInfos()) {
      appendApplyWindowInfoCmd(info1, commandList);
    }
    if (wasVisible || wasActive) {
      showToolWindowImpl(id, true, commandList);
    }
    if (wasActive) {
      activateToolWindowImpl(id, commandList, true, true);
    }
    commandList.add(myToolWindowsPane.createUpdateButtonPositionCmd(id, myCommandProcessor));
  }

  ToolWindowType getToolWindowInternalType(@NotNull String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getRegisteredInfoOrLogError(id).getInternalType();
  }

  ToolWindowType getToolWindowType(@NotNull String id) {
    return getRegisteredInfoOrLogError(id).getType();
  }

  private void fireToolWindowRegistered(@NotNull String id) {
    myProject.getMessageBus().syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowRegistered(id);
  }

  protected void fireStateChanged() {
    myProject.getMessageBus().syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged();
  }

  boolean isToolWindowActive(@NotNull String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getRegisteredInfoOrLogError(id).isActive();
  }

  boolean isToolWindowAutoHide(@NotNull String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getRegisteredInfoOrLogError(id).isAutoHide();
  }

  boolean isToolWindowVisible(@NotNull String id) {
    return getRegisteredInfoOrLogError(id).isVisible();
  }

  void setToolWindowAutoHide(@NotNull String id, final boolean autoHide) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<FinalizableCommand> commandList = new ArrayList<>();
    setToolWindowAutoHideImpl(id, autoHide, commandList);
    execute(commandList);
  }

  private void setToolWindowAutoHideImpl(@NotNull String id, final boolean autoHide, @NotNull List<? super FinalizableCommand> commandsList) {
    final WindowInfoImpl info = getRegisteredInfoOrLogError(id);
    if (info.isAutoHide() == autoHide) {
      return;
    }
    info.setAutoHide(autoHide);
    appendApplyWindowInfoCmd(info, commandsList);
    if (info.isVisible()) {
      deactivateWindows(id, commandsList);
      showAndActivate(id, false, commandsList, true);
    }
  }

  void setToolWindowType(@NotNull String id, @NotNull ToolWindowType type) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<FinalizableCommand> commandList = new ArrayList<>();
    setToolWindowTypeImpl(id, type, commandList);
    execute(commandList);
  }

  private void setToolWindowTypeImpl(@NotNull String id, @NotNull ToolWindowType type, @NotNull List<? super FinalizableCommand> commandsList) {
    final WindowInfoImpl info = getRegisteredInfoOrLogError(id);
    if (info.getType() == type) {
      return;
    }
    if (info.isVisible()) {
      final boolean dirtyMode = info.isDocked() || info.isSliding();
      info.setVisible(false);
      if (info.isFloating()) {
        appendRemoveFloatingDecoratorCmd(info, commandsList);
      }
      else if (info.isWindowed()) {
        appendRemoveWindowedDecoratorCmd(info, commandsList);
      }
      else { // docked and sliding windows
        appendRemoveDecoratorCmd(id, dirtyMode, commandsList);
      }
      info.setType(type);
      appendApplyWindowInfoCmd(info, commandsList);
      deactivateWindows(id, commandsList);
      showAndActivate(id, dirtyMode, commandsList, true);
      appendUpdateToolWindowsPaneCmd(commandsList);
    }
    else {
      info.setType(type);
      appendApplyWindowInfoCmd(info, commandsList);
    }
  }

  private void appendApplyWindowInfoCmd(@NotNull WindowInfoImpl info, @NotNull List<? super FinalizableCommand> commandsList) {
    final StripeButton button = getStripeButton(Objects.requireNonNull(info.getId()));
    final InternalDecorator decorator = getInternalDecorator(info.getId());
    commandsList.add(new ApplyWindowInfoCmd(info, button, decorator, myCommandProcessor));
  }

  /**
   * @see ToolWindowsPane#createAddDecoratorCmd
   */
  private void appendAddDecoratorCmd(@NotNull InternalDecorator decorator,
                                     @NotNull WindowInfoImpl info,
                                     boolean dirtyMode,
                                     @NotNull List<? super FinalizableCommand> commandsList) {
    commandsList.add(myToolWindowsPane.createAddDecoratorCmd(decorator, info, dirtyMode, myCommandProcessor));
  }

  /**
   * @see ToolWindowsPane#createRemoveDecoratorCmd
   */
  private void appendRemoveDecoratorCmd(@NotNull String id, final boolean dirtyMode, @NotNull List<? super FinalizableCommand> commandsList) {
    commandsList.add(myToolWindowsPane.createRemoveDecoratorCmd(id, dirtyMode, myCommandProcessor));
  }

  private void appendRemoveFloatingDecoratorCmd(@NotNull WindowInfoImpl info, @NotNull List<? super FinalizableCommand> commandsList) {
    commandsList.add(new RemoveFloatingDecoratorCmd(info));
  }

  private void appendRemoveWindowedDecoratorCmd(@NotNull WindowInfoImpl info, @NotNull List<? super FinalizableCommand> commandsList) {
    commandsList.add(new RemoveWindowedDecoratorCmd(info));
  }

  /**
   * @see ToolWindowsPane#createAddButtonCmd
   */
  private void appendAddButtonCmd(final StripeButton button, @NotNull WindowInfoImpl info, @NotNull List<? super FinalizableCommand> commandsList) {
    Comparator<StripeButton> comparator = myLayout.comparator(info.getAnchor());
    commandsList.add(myToolWindowsPane.createAddButtonCmd(button, info, comparator, myCommandProcessor));
  }

  /**
   * @see ToolWindowsPane#createAddButtonCmd
   */
  private void appendRemoveButtonCmd(@NotNull String id, @NotNull WindowInfoImpl info, @NotNull List<? super FinalizableCommand> commandsList) {
    FinalizableCommand cmd = myToolWindowsPane.createRemoveButtonCmd(info, id, myCommandProcessor);
    if (cmd != null) {
      commandsList.add(cmd);
    }
  }

  private void appendRequestFocusInToolWindowCmd(final String id, List<? super FinalizableCommand> commandList) {
    final ToolWindowImpl toolWindow = (ToolWindowImpl)getToolWindow(id);
    final FocusWatcher focusWatcher = myId2FocusWatcher.get(id);
    commandList
      .add(new RequestFocusInToolWindowCmd(getFocusManager(), toolWindow, focusWatcher, myCommandProcessor, myProject));
  }

  /**
   * @see ToolWindowsPane#createSetEditorComponentCmd
   */
  private void appendSetEditorComponentCmd(@Nullable final JComponent component, @NotNull List<? super FinalizableCommand> commandsList) {
    commandsList.add(myToolWindowsPane.createSetEditorComponentCmd(component, myCommandProcessor));
  }

  private void appendUpdateToolWindowsPaneCmd(final List<? super FinalizableCommand> commandsList) {
    JRootPane rootPane = myFrame.getRootPane();
    if (rootPane != null) {
      commandsList.add(new UpdateRootPaneCmd(rootPane, myCommandProcessor));
    }
  }

  private EditorsSplitters getSplittersToFocus() {
    Window activeWindow = myWindowManager.getMostRecentFocusedWindow();

    if (activeWindow instanceof FloatingDecorator) {
      IdeFocusManager ideFocusManager = IdeFocusManager.findInstanceByComponent(activeWindow);
      IdeFrame lastFocusedFrame = ideFocusManager.getLastFocusedFrame();
      JComponent frameComponent = lastFocusedFrame != null ? lastFocusedFrame.getComponent() : null;
      Window lastFocusedWindow = frameComponent != null ? SwingUtilities.getWindowAncestor(frameComponent) : null;
      activeWindow = ObjectUtils.notNull(lastFocusedWindow, activeWindow);
      FileEditorManagerEx fem = FileEditorManagerEx.getInstanceEx(Objects.requireNonNull(lastFocusedFrame.getProject()));
      EditorsSplitters splitters = fem.getSplittersFor(activeWindow);
      return splitters != null ? splitters : fem.getSplitters();
    }

    if (activeWindow instanceof IdeFrame.Child) {
      Project project = ((IdeFrame.Child)activeWindow).getProject();
      activeWindow = WindowManager.getInstance().getFrame(project);
      FileEditorManagerEx fem = FileEditorManagerEx.getInstanceEx(Objects.requireNonNull(project));
      EditorsSplitters splitters = activeWindow != null ? fem.getSplittersFor(activeWindow) : null;
      return splitters != null ? splitters : fem.getSplitters();
    }

    final IdeFrame frame = FocusManagerImpl.getInstance().getLastFocusedFrame();
    if (frame instanceof IdeFrameImpl && ((IdeFrameImpl)frame).isActive()) {
      FileEditorManagerEx fem = FileEditorManagerEx.getInstanceEx(Objects.requireNonNull(frame.getProject()));
      EditorsSplitters splitters = activeWindow != null ? fem.getSplittersFor(activeWindow) : null;
      return splitters != null ? splitters : fem.getSplitters();
    }

    return null;
  }

  @Override
  public void clearSideStack() {
    mySideStack.clear();
  }

  @Nullable
  @Override
  public Element getState() {
    if (myFrame == null) {
      // do nothing if the project was not opened
      return null;
    }

    // Update size of all open floating windows. See SCR #18439
    for (WindowInfoImpl info : myLayout.getInfos()) {
      if (info.isVisible()) {
        final InternalDecorator decorator = getInternalDecorator(Objects.requireNonNull(info.getId()));
        LOG.assertTrue(decorator != null);
        decorator.fireResized();
      }
    }

    Element element = new Element("state");

    // Save frame's bounds
    // [tav] Where we load these bounds? Should we just remove this code? (because we load frame bounds in WindowManagerImpl.allocateFrame)
    // Anyway, we should save bounds in device space to preserve backward compatibility with the IDE-managed HiDPI mode (see JBUI.ScaleType).
    // However, I won't change thise code because I can't even test it.
    final Rectangle frameBounds = myFrame.getBounds();
    final Element frameElement = new Element(FRAME_ELEMENT);
    element.addContent(frameElement);
    frameElement.setAttribute(X_ATTR, Integer.toString(frameBounds.x));
    frameElement.setAttribute(Y_ATTR, Integer.toString(frameBounds.y));
    frameElement.setAttribute(WIDTH_ATTR, Integer.toString(frameBounds.width));
    frameElement.setAttribute(HEIGHT_ATTR, Integer.toString(frameBounds.height));
    frameElement.setAttribute(EXTENDED_STATE_ATTR, Integer.toString(myFrame.getExtendedState()));

    // Save whether editor is active or not
    if (isEditorComponentActive()) {
      Element editorElement = new Element(EDITOR_ELEMENT);
      editorElement.setAttribute(ACTIVE_ATTR_VALUE, "true");
      element.addContent(editorElement);
    }

    // Save layout of tool windows
    Element layoutElement = myLayout.writeExternal(DesktopLayout.TAG);
    if (layoutElement != null) {
      element.addContent(layoutElement);
    }

    Element layoutToRestoreElement = myLayoutToRestoreLater == null ? null : myLayoutToRestoreLater.writeExternal(LAYOUT_TO_RESTORE);
    if (layoutToRestoreElement != null) {
      element.addContent(layoutToRestoreElement);
    }

    return element;
  }

  @Override
  public void loadState(@NotNull Element state) {
    for (Element e : state.getChildren()) {
      if (DesktopLayout.TAG.equals(e.getName())) {
        myLayout.readExternal(e);
      }
      else if (LAYOUT_TO_RESTORE.equals(e.getName())) {
        myLayoutToRestoreLater = new DesktopLayout();
        myLayoutToRestoreLater.readExternal(e);
      }
    }
  }

  public void setDefaultState(@NotNull final ToolWindowImpl toolWindow,
                              @Nullable final ToolWindowAnchor anchor,
                              @Nullable final ToolWindowType type,
                              @Nullable final Rectangle floatingBounds) {
    final WindowInfoImpl info = getRegisteredInfoOrLogError(toolWindow.getId());
    if (info.isWasRead()) return;

    if (floatingBounds != null) {
      info.setFloatingBounds(floatingBounds);
    }

    if (anchor != null) {
      toolWindow.setAnchor(anchor, null);
    }

    if (type != null) {
      toolWindow.setType(type, null);
    }
  }

  void setDefaultContentUiType(@NotNull ToolWindowImpl toolWindow, @NotNull ToolWindowContentUiType type) {
    final WindowInfoImpl info = getRegisteredInfoOrLogError(toolWindow.getId());
    if (info.isWasRead()) {
      return;
    }
    toolWindow.setContentUiType(type, null);
  }

  void stretchWidth(@NotNull ToolWindowImpl toolWindow, int value) {
    myToolWindowsPane.stretchWidth(toolWindow, value);
  }

  @Override
  public boolean isMaximized(@NotNull ToolWindow wnd) {
    return myToolWindowsPane.isMaximized(wnd);
  }

  @Override
  public void setMaximized(@NotNull ToolWindow wnd, boolean maximized) {
    if (wnd.getType() == ToolWindowType.FLOATING && wnd instanceof ToolWindowImpl) {
      MaximizeActiveDialogAction.doMaximize(getFloatingDecorator(((ToolWindowImpl)wnd).getId()));
      return;
    }
    if (wnd.getType() == ToolWindowType.WINDOWED && wnd instanceof ToolWindowImpl) {
      WindowedDecorator decorator = getWindowedDecorator(((ToolWindowImpl)wnd).getId());
      Frame frame = decorator != null && decorator.getFrame() instanceof Frame ? (Frame)decorator.getFrame() : null;
      if (frame != null) {
        int state = frame.getState();
        if (state == Frame.NORMAL) {
          frame.setState(Frame.MAXIMIZED_BOTH);
        }
        else if (state == Frame.MAXIMIZED_BOTH) {
          frame.setState(Frame.NORMAL);
        }
      }
      return;
    }
    myToolWindowsPane.setMaximized(wnd, maximized);
  }

  void stretchHeight(ToolWindowImpl toolWindow, int value) {
    myToolWindowsPane.stretchHeight(toolWindow, value);
  }

  private static class BalloonHyperlinkListener implements HyperlinkListener {
    private Balloon myBalloon;
    private final HyperlinkListener myListener;

    BalloonHyperlinkListener(HyperlinkListener listener) {
      myListener = listener;
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (myBalloon != null && e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        myBalloon.hide();
      }
      if (myListener != null) {
        myListener.hyperlinkUpdate(e);
      }
    }
  }

  /**
   * This command creates and shows {@code FloatingDecorator}.
   */
  private final class AddFloatingDecoratorCmd extends FinalizableCommand {
    private final FloatingDecorator myFloatingDecorator;

    /**
     * Creates floating decorator for specified internal decorator.
     */
    private AddFloatingDecoratorCmd(@NotNull InternalDecorator decorator, @NotNull WindowInfoImpl info) {
      super(myCommandProcessor);
      myFloatingDecorator = new FloatingDecorator(myFrame, info.copy(), decorator);
      myId2FloatingDecorator.put(info.getId(), myFloatingDecorator);
      final Rectangle bounds = info.getFloatingBounds();
      if (bounds != null &&
          bounds.width > 0 &&
          bounds.height > 0 &&
          myWindowManager.isInsideScreenBounds(bounds.x, bounds.y, bounds.width)) {
        myFloatingDecorator.setBounds(new Rectangle(bounds));
      }
      else {
        // place new frame at the center of main frame if there are no floating bounds
        Dimension size = decorator.getSize();
        if (size.width == 0 || size.height == 0) {
          size = decorator.getPreferredSize();
        }
        myFloatingDecorator.setSize(size);
        myFloatingDecorator.setLocationRelativeTo(myFrame);
      }
    }

    @Override
    public void run() {
      try {
        myFloatingDecorator.show();
      }
      finally {
        finish();
      }
    }
  }

  /**
   * This command hides and destroys floating decorator for tool window
   * with specified {@code ID}.
   */
  private final class RemoveFloatingDecoratorCmd extends FinalizableCommand {
    private final FloatingDecorator myFloatingDecorator;

    private RemoveFloatingDecoratorCmd(@NotNull WindowInfoImpl info) {
      super(myCommandProcessor);
      myFloatingDecorator = getFloatingDecorator(Objects.requireNonNull(info.getId()));
      myId2FloatingDecorator.remove(info.getId());
      info.setFloatingBounds(myFloatingDecorator.getBounds());
    }

    @Override
    public void run() {
      try {
        myFloatingDecorator.dispose();
      }
      finally {
        finish();
      }
    }

    @NotNull
    @Override
    public Condition getExpireCondition() {
      return ApplicationManager.getApplication().getDisposed();
    }
  }

  /**
   * This command creates and shows {@code WindowedDecorator}.
   */
  private final class AddWindowedDecoratorCmd extends FinalizableCommand {
    private final WindowedDecorator myWindowedDecorator;
    private final boolean myShouldBeMaximized;

    /**
     * Creates windowed decorator for specified internal decorator.
     */
    private AddWindowedDecoratorCmd(@NotNull InternalDecorator decorator, @NotNull WindowInfoImpl info) {
      super(myCommandProcessor);
      myWindowedDecorator = new WindowedDecorator(myProject, info.copy(), decorator);
      myShouldBeMaximized = info.isMaximized();
      Window window = myWindowedDecorator.getFrame();
      final Rectangle bounds = info.getFloatingBounds();
      if (bounds != null &&
          bounds.width > 0 &&
          bounds.height > 0 &&
          myWindowManager.isInsideScreenBounds(bounds.x, bounds.y, bounds.width)) {
        window.setBounds(new Rectangle(bounds));
      }
      else { // place new frame at the center of main frame if there are no floating bounds
        Dimension size = decorator.getSize();
        if (size.width == 0 || size.height == 0) {
          size = decorator.getPreferredSize();
        }
        window.setSize(size);
        window.setLocationRelativeTo(myFrame);
      }
      myId2WindowedDecorator.put(info.getId(), myWindowedDecorator);
      myWindowedDecorator.addDisposable(() -> {
        if (myId2WindowedDecorator.get(info.getId()) != null) {
          hideToolWindow(Objects.requireNonNull(info.getId()), false);
        }
      });
    }

    @Override
    public void run() {
      try {
        myWindowedDecorator.show(false);
        Window window = myWindowedDecorator.getFrame();
        JRootPane rootPane = ((RootPaneContainer)window).getRootPane();
        Rectangle rootPaneBounds = rootPane.getBounds();
        Point point = rootPane.getLocationOnScreen();
        Rectangle windowBounds = window.getBounds();
        //Point windowLocation = windowBounds.getLocation();
        //windowLocation.translate(windowLocation.x - point.x, windowLocation.y - point.y);
        window.setLocation(2 * windowBounds.x - point.x,  2 * windowBounds.y - point.y);
        window.setSize(2 * windowBounds.width - rootPaneBounds.width, 2 * windowBounds.height - rootPaneBounds.height);
        if (myShouldBeMaximized && window instanceof Frame) {
          ((Frame)window).setExtendedState(Frame.MAXIMIZED_BOTH);
        }
        window.toFront();
      }
      finally {
        finish();
      }
    }
  }

  /**
   * This command hides and destroys floating decorator for tool window
   * with specified {@code ID}.
   */
  private final class RemoveWindowedDecoratorCmd extends FinalizableCommand {
    private final WindowedDecorator myWindowedDecorator;

    private RemoveWindowedDecoratorCmd(@NotNull WindowInfoImpl info) {
      super(myCommandProcessor);
      myWindowedDecorator = getWindowedDecorator(Objects.requireNonNull(info.getId()));
      myId2WindowedDecorator.remove(info.getId());

      Window frame = myWindowedDecorator.getFrame();
      if (!frame.isShowing()) return;
      boolean maximized = ((JFrame)frame).getExtendedState() == Frame.MAXIMIZED_BOTH;
      if (maximized) {
        ((JFrame)frame).setExtendedState(Frame.NORMAL);
        frame.invalidate();
        frame.revalidate();
      }
      Rectangle bounds = getRootBounds((JFrame)frame);
      info.setFloatingBounds(bounds);
      info.setMaximized(maximized);
    }

    @Override
    public void run() {
      try {
        Disposer.dispose(myWindowedDecorator);
      }
      finally {
        finish();
      }
    }

    @NotNull
    @Override
    public Condition getExpireCondition() {
      return ApplicationManager.getApplication().getDisposed();
    }
  }

  @NotNull
  private static Rectangle getRootBounds(JFrame frame) {
    JRootPane rootPane = frame.getRootPane();
    Rectangle bounds = rootPane.getBounds();
    bounds.setLocation(frame.getX() + rootPane.getX(), frame.getY() + rootPane.getY());
    return bounds;
  }

  /**
   * Notifies window manager about focus traversal in tool window
   */
  private final class ToolWindowFocusWatcher extends FocusWatcher {
    private final String myId;
    private final ToolWindowImpl myToolWindow;

    private ToolWindowFocusWatcher(@NotNull ToolWindowImpl toolWindow) {
      myId = toolWindow.getId();
      install(toolWindow.getComponent());
      myToolWindow = toolWindow;
    }

    public void deinstall() {
      deinstall(myToolWindow.getComponent());
    }

    @Override
    protected boolean isFocusedComponentChangeValid(final Component comp, final AWTEvent cause) {
      return myCommandProcessor.getCommandCount() == 0 && comp != null;
    }

    @Override
    protected void focusedComponentChanged(final Component component, final AWTEvent cause) {
      if (myCommandProcessor.getCommandCount() > 0 || component == null) {
        return;
      }
      final WindowInfoImpl info = getRegisteredInfoOrLogError(myId);
      //getFocusManagerImpl(myProject)..cancelAllRequests();

      if (!info.isActive()) {
        getFocusManagerImpl(myProject).doWhenFocusSettlesDown(new EdtRunnable() {
          @Override
          public void runEdt() {
            WindowInfoImpl windowInfo = myLayout.getInfo(myId, true);
            if (windowInfo == null || !windowInfo.isVisible()) return;
            activateToolWindow(myId, false, false);
          }
        });
      }
    }
  }

  /**
   * Spies on IdeToolWindow properties and applies them to the window
   * state.
   */
  private final class MyToolWindowPropertyChangeListener implements PropertyChangeListener {
    @Override
    public void propertyChange(final PropertyChangeEvent e) {
      ToolWindowImpl toolWindow = (ToolWindowImpl)e.getSource();
      if (ToolWindowEx.PROP_AVAILABLE.equals(e.getPropertyName())) {
        final WindowInfoImpl info = getRegisteredInfoOrLogError(toolWindow.getId());
        if (!toolWindow.isAvailable() && info.isVisible()) {
          hideToolWindow(toolWindow.getId(), false);
        }
      }
      StripeButton button = myId2StripeButton.get(toolWindow.getId());
      if (button != null) button.updatePresentation();
      ActivateToolWindowAction.updateToolWindowActionPresentation(toolWindow);
    }
  }

  /**
   * Translates events from InternalDecorator into ToolWindowManager method invocations.
   */
  private final class MyInternalDecoratorListener implements InternalDecoratorListener {
    @Override
    public void anchorChanged(@NotNull final InternalDecorator source, @NotNull final ToolWindowAnchor anchor) {
      setToolWindowAnchor(source.getToolWindow().getId(), anchor);
    }

    @Override
    public void autoHideChanged(@NotNull final InternalDecorator source, final boolean autoHide) {
      setToolWindowAutoHide(source.getToolWindow().getId(), autoHide);
    }

    @Override
    public void hidden(@NotNull final InternalDecorator source) {
      hideToolWindow(source.getToolWindow().getId(), false);
    }

    @Override
    public void hiddenSide(@NotNull final InternalDecorator source) {
      hideToolWindow(source.getToolWindow().getId(), true);
    }

    @Override
    public void contentUiTypeChanges(@NotNull InternalDecorator source, @NotNull ToolWindowContentUiType type) {
      setContentUiType(source.getToolWindow().getId(), type);
    }

    /**
     * Handles event from decorator and modify weight/floating bounds of the
     * tool window depending on decoration type.
     */
    @Override
    public void resized(@NotNull final InternalDecorator source) {
      if (!source.isShowing()) {
        return; // do not recalculate the tool window size if it is not yet shown (and, therefore, has 0,0,0,0 bounds)
      }

      final WindowInfoImpl info = getRegisteredInfoOrLogError(source.getToolWindow().getId());
      if (info.isFloating()) {
        final Window owner = SwingUtilities.getWindowAncestor(source);
        if (owner != null) {
          info.setFloatingBounds(owner.getBounds());
        }
      }
      else if (info.isWindowed()) {
        WindowedDecorator decorator = getWindowedDecorator(Objects.requireNonNull(info.getId()));
        Window frame = decorator != null ? decorator.getFrame() : null;
        if (frame == null || !frame.isShowing()) return;
        info.setFloatingBounds(getRootBounds((JFrame)frame));
      }
      else { // docked and sliding windows
        ToolWindowAnchor anchor = info.getAnchor();
        InternalDecorator another = null;
        if (source.getParent() instanceof Splitter) {
          float sizeInSplit = anchor.isSplitVertically() ? source.getHeight() : source.getWidth();
          Splitter splitter = (Splitter)source.getParent();
          if (splitter.getSecondComponent() == source) {
            sizeInSplit += splitter.getDividerWidth();
            another = (InternalDecorator)splitter.getFirstComponent();
          }
          else {
            another = (InternalDecorator)splitter.getSecondComponent();
          }
          if (anchor.isSplitVertically()) {
            info.setSideWeight(sizeInSplit / splitter.getHeight());
          }
          else {
            info.setSideWeight(sizeInSplit / splitter.getWidth());
          }
        }

        float paneWeight = anchor.isHorizontal()
                           ? (float)source.getHeight() / myToolWindowsPane.getMyLayeredPane().getHeight()
                           : (float)source.getWidth() / myToolWindowsPane.getMyLayeredPane().getWidth();
        info.setWeight(paneWeight);
        if (another != null && anchor.isSplitVertically()) {
          paneWeight = anchor.isHorizontal()
                       ? (float)another.getHeight() / myToolWindowsPane.getMyLayeredPane().getHeight()
                       : (float)another.getWidth() / myToolWindowsPane.getMyLayeredPane().getWidth();
          another.getWindowInfo().setWeight(paneWeight);
        }
      }
    }

    @Override
    public void activated(@NotNull final InternalDecorator source) {
      activateToolWindow(source.getToolWindow().getId(), true, true);
    }

    @Override
    public void typeChanged(@NotNull final InternalDecorator source, @NotNull final ToolWindowType type) {
      setToolWindowType(source.getToolWindow().getId(), type);
    }

    @Override
    public void sideStatusChanged(@NotNull final InternalDecorator source, final boolean isSideTool) {
      setSideTool(source.getToolWindow().getId(), isSideTool);
    }

    @Override
    public void visibleStripeButtonChanged(@NotNull InternalDecorator source, boolean visible) {
      setShowStripeButton(source.getToolWindow().getId(), visible);
    }
  }

  private void focusToolWindowByDefault(@Nullable String idToIgnore) {
    String toFocus = null;

    for (String each : myActiveStack.getStack()) {
      if (idToIgnore != null && idToIgnore.equalsIgnoreCase(each)) continue;

      if (getRegisteredInfoOrLogError(each).isVisible()) {
        toFocus = each;
        break;
      }
    }

    if (toFocus == null) {
      for (String each : myActiveStack.getPersistentStack()) {
        if (idToIgnore != null && idToIgnore.equalsIgnoreCase(each)) continue;

        if (getRegisteredInfoOrLogError(each).isVisible()) {
          toFocus = each;
          break;
        }
      }
    }

    if (toFocus != null && !ApplicationManager.getApplication().isDisposeInProgress() && !ApplicationManager.getApplication().isDisposed()) {
      activateToolWindow(toFocus, false, true);
    }
  }

  /**
   * Delegate method for compatibility with older versions of IDEA
   */
  @NotNull
  public ActionCallback requestFocus(@NotNull Component c, boolean forced) {
    return IdeFocusManager.getInstance(myProject).requestFocus(c, forced);
  }

  public void doWhenFocusSettlesDown(@NotNull Runnable runnable) {
    IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(runnable);
  }

  void setShowStripeButton(@NotNull String id, boolean visibleOnPanel) {
    WindowInfoImpl info = getRegisteredInfoOrLogError(id);
    if (visibleOnPanel == info.isShowStripeButton()) {
      return;
    }
    info.setShowStripeButton(visibleOnPanel);

    List<FinalizableCommand> commandList = new ArrayList<>();
    appendApplyWindowInfoCmd(info, commandList);
    execute(commandList);
  }

  boolean isShowStripeButton(@NotNull String id) {
    WindowInfoImpl info = myLayout.getInfo(id, true);
    return info == null || info.isShowStripeButton();
  }

  public static class InitToolWindowsActivity implements StartupActivity, DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      ToolWindowManagerEx ex = ToolWindowManagerEx.getInstanceEx(project);
      if (ex instanceof ToolWindowManagerImpl) {
        ToolWindowManagerImpl manager = (ToolWindowManagerImpl)ex;
        List<FinalizableCommand> list = new ArrayList<>();
        manager.registerToolWindowsFromBeans(list);
        manager.initAll(list);
        EdtInvocationManager.getInstance().invokeLater(() -> {
          manager.execute(list);
          manager.flushCommands();
        });
      }
    }
  }
}