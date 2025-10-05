// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.impl;

import com.intellij.concurrency.ThreadContext;
import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.CommandProcessorEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.popup.StackingPopupDispatcher;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.WindowStateService;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomFrameDialogContent;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomHeader;
import com.intellij.platform.ide.bootstrap.SplashManagerKt;
import com.intellij.platform.locking.impl.IntelliJLockingUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.ui.mac.touchbar.TouchbarSupport;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.*;
import kotlin.Pair;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.functions.Function0;
import kotlinx.coroutines.EventLoop;
import kotlinx.coroutines.ThreadLocalEventLoop;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferStrategy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DialogWrapperPeerImpl extends DialogWrapperPeer {
  @SuppressWarnings("LoggerInitializedWithForeignClass")
  private static final Logger LOG = Logger.getInstance(DialogWrapper.class);

  public static boolean isHeadlessEnv() {
    Application app = ApplicationManager.getApplication();
    return app == null ? GraphicsEnvironment.isHeadless() : app.isUnitTestMode() || app.isHeadlessEnvironment();
  }

  private final DialogWrapper myWrapper;
  private final AbstractDialog myDialog;
  private final boolean myCanBeParent;
  private final List<Runnable> myDisposeActions = new ArrayList<>();
  private Project myProject;

  protected DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper,
                                  @Nullable Project project,
                                  boolean canBeParent,
                                  @NotNull DialogWrapper.IdeModalityType ideModalityType) {
    boolean headless = isHeadlessEnv();
    myWrapper = wrapper;

    Window window = null;
    if (LoadingState.COMPONENTS_LOADED.isOccurred()) {
      WindowManagerEx windowManager = getWindowManager();
      if (windowManager != null) {
        Window curWindow = ObjectUtils.chooseNotNull(
          windowManager.getMostRecentFocusedWindow(),
          KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow());
        if (project == null && curWindow != null) {
          project = ProjectUtil.getProjectForWindow(curWindow);
        }

        myProject = project;

        window = windowManager.suggestParentWindow(project);
        if (window == null) {
          if (curWindow instanceof IdeFrameImpl) {
            window = curWindow;
          }
        }
        if (window == null) {
          for (ProjectFrameHelper frameHelper : windowManager.getProjectFrameHelpers()) {
            IdeFrameImpl frame = frameHelper.getFrame();
            if (frame.isActive()) {
              window = frameHelper.getFrame();
              break;
            }
          }
        }
      }
    }

    Window owner;
    if (window != null) {
      owner = window;
    }
    else if (!headless) {
      owner = JOptionPane.getRootFrame();
    }
    else {
      owner = null;
    }

    myDialog = createDialog(headless, owner, wrapper, myProject, ideModalityType);
    myCanBeParent = headless || canBeParent;
  }

  /**
   * Creates modal {@code DialogWrapper}. The currently active window will be the dialog's parent.
   *
   * @param project     parent window for the dialog will be calculated based on focused window for the
   *                    specified {@code project}. This parameter can be {@code null}. In this case parent window
   *                    will be suggested based on current focused window.
   * @param canBeParent specifies whether the dialog can be parent for other windows. This parameter is used
   *                    by {@code WindowManager}.
   */
  protected DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper, @Nullable Project project, boolean canBeParent) {
    this(wrapper, project, canBeParent, DialogWrapper.IdeModalityType.IDE);
  }

  protected DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper, boolean canBeParent) {
    this(wrapper, (Project)null, canBeParent);
  }

  /**
   * @param parent parent component (must be showing) which is used to calculate heavyweight window ancestor.
   */
  protected DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper, @NotNull Component parent, boolean canBeParent) {
    boolean headless = isHeadlessEnv();
    myWrapper = wrapper;
    myDialog = createDialog(headless, OwnerOptional.findOwner(parent), wrapper, null, DialogWrapper.IdeModalityType.IDE);
    myCanBeParent = headless || canBeParent;
  }

  protected DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper, Window owner, boolean canBeParent, DialogWrapper.IdeModalityType ideModalityType) {
    var headless = isHeadlessEnv();
    myWrapper = wrapper;
    myDialog = createDialog(headless, owner, wrapper, null, DialogWrapper.IdeModalityType.IDE);
    myCanBeParent = headless || canBeParent;

    if (!headless) {
      Dialog.ModalityType modalityType = DialogWrapper.IdeModalityType.IDE.toAwtModality();
      myDialog.setModalityType(modalityType);
    }
  }

  private static WindowManagerEx getWindowManager() {
    Application app = LoadingState.COMPONENTS_LOADED.isOccurred() ? ApplicationManager.getApplication() : null;
    return app == null ? null : WindowManagerEx.getInstanceEx();
  }

  private static AbstractDialog createDialog(boolean headless, Window owner, DialogWrapper wrapper, Project project, DialogWrapper.IdeModalityType ideModalityType) {
    if (headless) {
      return new HeadlessDialog(wrapper);
    }
    else {
      var dialog = new MyDialog(OwnerOptional.findOwner(owner), wrapper, project);
      dialog.setModalityType(ideModalityType.toAwtModality());
      return dialog;
    }
  }

  @Override
  public boolean isHeadless() {
    return myDialog instanceof HeadlessDialog;
  }

  @Override
  public void setOnDeactivationAction(@NotNull Disposable disposable, @NotNull Runnable onDialogDeactivated) {
    WindowDeactivationManager.getInstance().addWindowDeactivationListener(getWindow(), myProject, disposable, onDialogDeactivated);
  }

  @Override
  public Object[] getCurrentModalEntities() {
    return LaterInvocator.getCurrentModalEntities();
  }

  @Override
  public void setUndecorated(boolean undecorated) {
    myDialog.setUndecorated(undecorated);
  }

  @Override
  public void addMouseListener(MouseListener listener) {
    myDialog.addMouseListener(listener);
  }

  @Override
  public void addMouseListener(MouseMotionListener listener) {
    myDialog.addMouseMotionListener(listener);
  }

  @Override
  public void addKeyListener(KeyListener listener) {
    myDialog.addKeyListener(listener);
  }

  @Override
  public void toFront() {
    myDialog.toFront();
  }

  @Override
  public void toBack() {
    myDialog.toBack();
  }

  @Override
  protected void dispose() {
    LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");
    for (Runnable runnable : myDisposeActions) {
      runnable.run();
    }
    myDisposeActions.clear();
    Runnable disposer = () -> {
      Disposer.dispose(myDialog);
      myProject = null;

      SwingUtilities.invokeLater(() -> {
        if (myDialog.getRootPane() != null) {
          myDialog.remove(myDialog.getRootPane());
        }
      });
    };

    EdtInvocationManager.invokeLaterIfNeeded(disposer);
  }

  private boolean isProgressDialog() {
    return myWrapper.isModalProgress();
  }

  @Override
  public @Nullable Container getContentPane() {
    return getRootPane() != null ? myDialog.getContentPane() : null;
  }

  /**
   * @see JDialog#validate
   */
  @Override
  public void validate() {
    myDialog.validate();
  }

  /**
   * @see JDialog#repaint
   */
  @Override
  public void repaint() {
    myDialog.repaint();
  }

  @Override
  public Window getOwner() {
    return myDialog.getOwner();
  }

  @Override
  public Window getWindow() {
    return myDialog.getWindow();
  }

  @Override
  public JRootPane getRootPane() {
    return myDialog.getRootPane();
  }

  @Override
  public Dimension getSize() {
    return myDialog.getSize();
  }

  @Override
  public String getTitle() {
    return myDialog.getTitle();
  }

  /**
   * @see Window#pack
   */
  @Override
  public void pack() {
    myDialog.pack();
  }

  @Override
  public void setAppIcons() {
    AppUIUtilKt.updateAppWindowIcon(getWindow());
  }

  @Override
  public Dimension getPreferredSize() {
    return myDialog.getPreferredSize();
  }

  @Override
  @SuppressWarnings("deprecation")
  public void setModal(boolean modal) {
    myDialog.setModal(modal);
  }

  @Override
  @SuppressWarnings("deprecation")
  public boolean isModal() {
    return myDialog.isModal();
  }

  @Override
  public boolean isVisible() {
    return myDialog.isVisible();
  }

  @Override
  public boolean isShowing() {
    return myDialog.isShowing();
  }

  @Override
  public void setSize(int width, int height) {
    myDialog.setSize(width, height);
  }

  @Override
  public void setTitle(String title) {
    myDialog.setTitle(title);
  }

  @Override
  public boolean isResizable() {
    return myDialog.isResizable();
  }

  @Override
  public void setResizable(boolean resizable) {
    myDialog.setResizable(resizable);
  }

  @Override
  public @NotNull Point getLocation() {
    return myDialog.getLocation();
  }

  @Override
  public void setLocation(@NotNull Point p) {
    myDialog.setLocation(p);
  }

  @Override
  public void setLocation(int x, int y) {
    myDialog.setLocation(x, y);
  }

  @Override
  public CompletableFuture<?> show() {
    LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");

    AnCancelAction anCancelAction = new AnCancelAction();
    JRootPane rootPane = getRootPane();
    ComponentUtil.decorateWindowHeader(rootPane);

    Window window = getWindow();
    if (window instanceof JDialog && !((JDialog)window).isUndecorated() && rootPane != null && LoadingState.COMPONENTS_LOADED.isOccurred()) {
      ToolbarService.Companion.getInstance().setTransparentTitleBar(window, rootPane, runnable -> {
        Disposer.register(myWrapper.getDisposable(), () -> runnable.run());
        return Unit.INSTANCE;
      });
    }

    Container contentPane = getContentPane();
    if (contentPane instanceof CustomFrameDialogContent) {
      ((CustomFrameDialogContent)contentPane).updateLayout();
    }

    Application app = LoadingState.COMPONENTS_LOADED.isOccurred() ? ApplicationManager.getApplication() : null;
    if (app != null && app.getServiceIfCreated(ActionManager.class) != null) {
      anCancelAction.registerCustomShortcutSet(ActionUtil.getShortcutSet(IdeActions.ACTION_EDITOR_ESCAPE), rootPane);
    }
    else {
      anCancelAction.registerCustomShortcutSet(CommonShortcuts.ESCAPE, rootPane);
    }

    if (rootPane != null) {
      myDisposeActions.add(() -> anCancelAction.unregisterCustomShortcutSet(rootPane));
    }

    if (app != null && !myCanBeParent) {
      WindowManagerEx windowManager = getWindowManager();
      if (windowManager != null) {
        windowManager.doNotSuggestAsParent(myDialog.getWindow());
      }
    }

    CommandProcessorEx commandProcessor = app == null ? null : (CommandProcessorEx)CommandProcessor.getInstance();
    boolean appStarted = commandProcessor != null;

    // ProgressWindow starts a modality state itself
    @SuppressWarnings("deprecation") boolean changeModalityState = appStarted && myDialog.isModal() && !isProgressDialog();
    Project project = myProject;

    Consumer<Runnable> lockContextWrapper;
    Function0<Unit> lockCleanup;

    if (changeModalityState) {
      commandProcessor.enterModal();
      LaterInvocator.enterModal(myDialog);

      var pair = ApplicationManager.getApplication().isWriteAccessAllowed()
                 ? new Pair<>(EmptyCoroutineContext.INSTANCE, emptyFunction)
                 : IntelliJLockingUtil.getGlobalThreadingSupport().getPermitAsContextElement(ThreadContext.currentThreadContext(), true);
      lockContextWrapper = (r) -> {
        ThreadContext.installThreadContext(pair.getFirst(), true, () -> {
          r.run();
          return Unit.INSTANCE;
        });
      };
      lockCleanup = pair.getSecond();
    }
    else {
      lockContextWrapper = (r) -> ThreadContext.resetThreadContext(() -> {
        r.run();
        return null;
      });
      lockCleanup = emptyFunction;
    }

    if (appStarted) {
      hidePopupsIfNeeded();
    }

    if (SystemInfoRt.isMac) {
      final Disposable tb = TouchbarSupport.showWindowActions(myDialog.getContentPane());
      if (tb != null) {
        myDisposeActions.add(() -> Disposer.dispose(tb));
      }
    }

    Component componentToRestoreFocus = null;
    KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    if (UIUtil.isSimpleWindow(kfm.getFocusedWindow())) {
      // 'Simple' windows cannot be dialog owners, so focus won't return to them automatically on dialog closing
      componentToRestoreFocus = kfm.getPermanentFocusOwner();
    }

    CompletableFuture<Void> result = new CompletableFuture<>();
    SplashManagerKt.hideSplash();
    try ( // numbered in the order of invocation (which is reverse)
      AccessToken ignore5 = AccessToken.create(() -> {
        if (changeModalityState) {
          LaterInvocator.leaveModal(myDialog);
        }
      });
      AccessToken ignore4 = AccessToken.create(() -> {
        if (changeModalityState) {
          commandProcessor.leaveModal();
        }
      });
      AccessToken ignore3 = AccessToken.create(() -> {
        lockCleanup.invoke();
      });
      AccessToken ignore2 = SlowOperations.startSection(SlowOperations.RESET);
      AccessToken ignore1 = resetCoroutinesEventLoop()
    ) {
      lockContextWrapper.accept(() -> {
        if (!isProgressDialog() &&
            ApplicationManager.getApplication() != null &&
            !ApplicationManager.getApplication().isReadAccessAllowed()) {
          WriteIntentReadAction.run((Runnable)() -> {
            myDialog.show();
          });
        }
        else {
          myDialog.show();
        }
      });
    }
    finally {
      myDialog.getFocusManager().doWhenFocusSettlesDown(() -> result.complete(null));
    }

    if (componentToRestoreFocus != null) {
      componentToRestoreFocus.requestFocus();
    }

    return result;
  }

  private static final Function0<Unit> emptyFunction = () -> Unit.INSTANCE;

  //hopefully this whole code will go away
  private void hidePopupsIfNeeded() {
    if (!SystemInfoRt.isMac) {
      return;
    }

    StackingPopupDispatcher.getInstance().hidePersistentPopups();
    myDisposeActions.add(() -> StackingPopupDispatcher.getInstance().restorePersistentPopups());
  }

  @RequiresEdt
  private static AccessToken resetCoroutinesEventLoop() {
    EventLoop currentEventLoop = ThreadLocalEventLoop.INSTANCE.currentOrNull$kotlinx_coroutines_core();
    ThreadLocalEventLoop.INSTANCE.resetEventLoop$kotlinx_coroutines_core();
    return new AccessToken() {
      @Override
      public void finish() {
        if (currentEventLoop != null) {
          ThreadLocalEventLoop.INSTANCE.resetEventLoop$kotlinx_coroutines_core();
        }
      }
    };
  }

  private final class AnCancelAction extends AnAction implements DumbAware {
    @Override
    public void update(@NotNull AnActionEvent e) {
      Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      e.getPresentation().setEnabled(false);
      if (focusOwner instanceof JComponent && SpeedSearchBase.hasActiveSpeedSearch((JComponent)focusOwner)) {
        return;
      }

      if (StackingPopupDispatcher.getInstance().isPopupFocused()) return;
      JTree tree = ComponentUtil.getParentOfType((Class<? extends JTree>)JTree.class, focusOwner);
      JTable table = ComponentUtil.getParentOfType((Class<? extends JTable>)JTable.class, focusOwner);

      if (tree != null || table != null) {
        if (hasNoEditingTreesOrTablesUpward(focusOwner)) {
          e.getPresentation().setEnabled(true);
        }
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    private static boolean hasNoEditingTreesOrTablesUpward(Component comp) {
      while (comp != null) {
        if (isEditingTreeOrTable(comp)) return false;
        comp = comp.getParent();
      }
      return true;
    }

    private static boolean isEditingTreeOrTable(Component comp) {
      if (comp instanceof JTree) {
        return ((JTree)comp).isEditing();
      }
      else if (comp instanceof JTable) {
        return ((JTable)comp).isEditing();
      }
      return false;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myWrapper.doCancelAction(e.getInputEvent());
    }
  }

  private static final class MyDialog extends JDialog implements DialogWrapperDialog, UiDataProvider, Queryable, AbstractDialog, DisposableWindow {
    private final WeakReference<DialogWrapper> myDialogWrapper;

    /**
     * Initial size of the dialog. When the dialog is being closed and
     * current size of the dialog is not equals to the initial size then the
     * current (changed) size is stored in the {@code DimensionService}.
     */
    private Dimension myInitialSize;
    private String myDimensionServiceKey;
    private boolean myOpened = false;
    private boolean myDisposed = false;

    private MyDialog.MyWindowListener myWindowListener;

    private final WeakReference<Project> myProject;

    private @Nullable MacDirtySizeHack myMacDirtySizeHack;

    MyDialog(Window owner,
                    DialogWrapper dialogWrapper,
                    Project project) {
      super(owner);
      myDialogWrapper = new WeakReference<>(dialogWrapper);
      myProject = project != null ? new WeakReference<>(project) : null;

      setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
        @Override
        public boolean accept(Component aComponent) {
          return !ComponentUtil.isFocusProxy(aComponent) && super.accept(aComponent);
        }
      });

      setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
      myWindowListener = new MyWindowListener();
      addWindowListener(myWindowListener);
      addWindowFocusListener(myWindowListener);
    }

    @Override
    public JDialog getWindow() {
      return this;
    }

    @Override
    public boolean isWindowDisposed() {
      return myDisposed;
    }

    @Override
    public void putInfo(@NotNull Map<? super String, ? super String> info) {
      info.put("dialog", getTitle());
    }

    @Override
    public DialogWrapper getDialogWrapper() {
      return myDialogWrapper.get();
    }

    @Override
    public void centerInParent() {
      setLocationRelativeTo(getOwner());
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      DialogWrapper wrapper = myDialogWrapper.get();
      DataSink.uiDataSnapshot(sink, wrapper);
      Project project = getProject();
      if (project != null && project.isInitialized()) {
        sink.set(CommonDataKeys.PROJECT, project);
      }
    }

    private void fitToScreen(Rectangle rect) {
      if (myDialogWrapper == null) return; // this can be invoked from super constructor before this field is assigned
      final DialogWrapper wrapper = myDialogWrapper.get();
      if (wrapper != null) wrapper.fitToScreen(rect);
    }

    @Override
    public void pack() {
      super.pack();
      DialogWrapper dialogWrapper = getDialogWrapper();
      if (dialogWrapper == null || dialogWrapper.setSizeDuringPack()) {
        // pack() already sets the size to the preferred size, but it may change during validation,
        // which is performed after the size is set. This happens, for example, if there are components
        // with soft wraps whose preferred height depends on their width.
        var preferredSize = getPreferredSize();
        super.setSize(preferredSize.width, preferredSize.height); // we don't want to call our own overload here
      }
    }

    @Override
    public void setLocation(int x, int y) {
      var sizeHack = myMacDirtySizeHack;
      if (sizeHack == null) {
        super.setLocation(x, y);
      }
      else {
        var hackSize = getSize();
        super.setBounds(x, y, hackSize.width, hackSize.height);
      }
    }

    @Override
    public void setSize(int width, int height) {
      _setSizeForLocation(width, height, null);
    }

    private void _setSizeForLocation(int width, int height, @Nullable Point initial) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Setting the size (" + width + "," + height + ") for location " + initial);
      }
      Point location = initial != null ? initial : getLocation();
      if (initial == null && LOG.isDebugEnabled()) {
        LOG.debug("Falling back to the actual location because the specified one is null: " + location);
      }
      Rectangle rect = new Rectangle(location.x, location.y, width, height);
      Rectangle before = null;
      if (LOG.isDebugEnabled()) {
        before = new Rectangle(rect);
      }
      fitToScreen(rect);
      if (LOG.isDebugEnabled() && !Objects.equals(before, rect)) {
        LOG.debug("Fitted these bounds to the screen: " + before + " -> " + rect);
      }
      if (initial != null || location.x != rect.x || location.y != rect.y) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Setting the location to (" + rect.x + "," + rect.y + ")");
        }
        setLocation(rect.x, rect.y);
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("Setting the size to (" + rect.width + "," + rect.height + ")");
      }
      super.setSize(rect.width, rect.height);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
      Rectangle rect = new Rectangle(x, y, width, height);
      fitToScreen(rect);
      super.setBounds(rect.x, rect.y, rect.width, rect.height);
    }

    @Override
    public void setBounds(Rectangle r) {
      fitToScreen(r);
      super.setBounds(r);
    }

    @SuppressWarnings("deprecation") // overridden for logging and hacking purposes, as other similar methods all delegate to this one
    @Override
    public void reshape(int x, int y, int width, int height) {
      if (LOG.isTraceEnabled()) {
        LOG.trace(new Throwable("DialogWrapperPeerImpl.MyDialog.reshape(title='" + getTitle() + "'): " + new Rectangle(x, y, width, height)));
      }
      var sizeHack = myMacDirtySizeHack;
      if (sizeHack != null) {
        sizeHack.updateSize(width, height);
      }
      super.reshape(x, y, width, height);
    }

    @Override
    public Dimension getSize() {
      var actualSize = super.getSize();
      var sizeHack = myMacDirtySizeHack;
      if (sizeHack != null) {
        var hackSize = sizeHack.getSize();
        if (LOG.isDebugEnabled() && !hackSize.equals(actualSize)) {
          LOG.debug("MacOS hack size override: actual size is " + actualSize + ", but we use " + hackSize + " instead");
        }
        return hackSize;
      }
      return actualSize;
    }

    @Override
    public @NotNull Rectangle getBounds() { // just delegate to the above
      return new Rectangle(getLocation(), getSize());
    }

    @Override
    protected @NotNull JRootPane createRootPane() {
      return new DialogRootPane();
    }

    @Override
    public void addNotify() {
      if (IdeFrameDecorator.Companion.isCustomDecorationActive()) {
        CustomHeader.Companion.enableCustomHeader(this);
      }
      super.addNotify();
      if (SystemInfo.isMacOSVentura && Registry.is("ide.mac.stage.manager.support", false)) {
        Foundation.executeOnMainThread(true, false, () -> {
          ID window = MacUtil.getWindowFromJavaWindow(this);
          Foundation.invoke(window, "setCollectionBehavior:", 1 << 4);
        });
      }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void show() {

      final DialogWrapper dialogWrapper = getDialogWrapper();
      boolean isAutoAdjustable = dialogWrapper.isAutoAdjustable();
      Point location = null;
      if (LOG.isDebugEnabled()) {
        LOG.debug("START preparing to show a dialog titled '" + getTitle() + "', isAutoAdjustable=" + isAutoAdjustable + ", the monitor configuration is:");
        logMonitorConfiguration();
      }
      if (isAutoAdjustable) {
        if (SystemInfoRt.isMac) {
          myMacDirtySizeHack = new MacDirtySizeHack(getWidth(), getHeight());
          addComponentListener(myMacDirtySizeHack);
        }
        pack();

        Dimension initial = dialogWrapper.getInitialSize();
        if (LOG.isDebugEnabled()) {
          LOG.debug("The initial size of the dialog as set/overridden by the API user: " + initial);
        }
        if (initial == null) initial = new Dimension();
        if (initial.width <= 0 || initial.height <= 0) {
          Dimension packedSize = getSize();
          if (LOG.isDebugEnabled()) {
            LOG.debug("The size of the dialog after packing: " + packedSize);
          }
          maximize(initial, packedSize); // cannot be less than packed size
          if (LOG.isDebugEnabled()) {
            LOG.debug("The initial size after coercing it to be at least the packed size: " + initial);
          }
          if (!SystemInfoRt.isLinux && Registry.is("ide.dialog.wrapper.resize.by.tables", false)) {
            // [kb] temporary workaround for IDEA-253643
            maximize(initial, getSizeForTableContainer(getContentPane()));
          }
        }
        Dimension minimumSize = getMinimumSize();
        if (LOG.isDebugEnabled()) {
          LOG.debug("The minimum size of the dialog: " + minimumSize);
        }
        maximize(initial, minimumSize); // cannot be less than minimum size
        if (LOG.isDebugEnabled()) {
          LOG.debug("The initial size after coercing it to be at least the minimum size: " + initial);
        }
        float hs = dialogWrapper.getHorizontalStretch();
        initial.width *= (int)hs;
        float vs = dialogWrapper.getVerticalStretch();
        initial.height *= (int)vs;
        if (LOG.isDebugEnabled()) {
          LOG.debug("Setting the initial size after stretching it by (" + hs + "," + vs + "): " + initial);
        }
        setSize(initial);

        // Restore dialog's size and location

        myDimensionServiceKey = dialogWrapper.getDimensionKey();
        if (LOG.isDebugEnabled()) {
          LOG.debug("The dimension service key is '" + myDimensionServiceKey + "'");
        }

        if (myDimensionServiceKey != null) {
          final Project projectGuess = guessProjectDependingOnKey(myDimensionServiceKey);
          if (LOG.isDebugEnabled()) {
            LOG.debug("The project is " + projectGuess);
          }
          location = getWindowStateService(projectGuess).getLocation(myDimensionServiceKey);
          Dimension size = getWindowStateService(projectGuess).getSize(myDimensionServiceKey);
          if (LOG.isDebugEnabled()) {
            LOG.debug("The stored location and size are " + location + " and " + size + (size != null ? ", using them to set the initial bounds" : ""));
          }
          if (size != null) {
            myInitialSize = new Dimension(size);
            _setSizeForLocation(myInitialSize.width, myInitialSize.height, location);
          }
        }

        if (myInitialSize == null) {
          Dimension initialSize = dialogWrapper.getInitialSize();
          myInitialSize = initialSize != null ? initialSize : getSize();
        }
      }

      if (location == null) {
        location = dialogWrapper.getInitialLocation();
        if (LOG.isDebugEnabled()) {
          LOG.debug("The initial location is " + location);
        }
      }

      if (location != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Setting the location to " + location);
        }
        setLocation(location);
      }
      else {
        Window owner = getOwner();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Setting the location relative to " + owner);
        }
        setLocationRelativeTo(owner);
      }

      if (isAutoAdjustable) {
        final Rectangle bounds = getBounds();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Performing auto-adjustment for the resulting bounds: " + bounds);
        }
        fitToScreen(bounds);
        if (LOG.isDebugEnabled()) {
          LOG.debug("The bounds after fitting to screen: " + bounds);
        }
        setBounds(bounds);
      }

      DialogWrapper wrapper = myDialogWrapper == null ? null : myDialogWrapper.get();
      if (wrapper != null) {
        wrapper.beforeShowCallback();
      }

      if (!SystemInfoRt.isMac || !WindowRoundedCornersManager.isAvailable()) {
        setBackground(UIUtil.getPanelBackground());
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("END preparing to show the dialog, the resulting bounds: " + getBounds());
      }
      if (dialogWrapper.isDisposed()) {
        LOG.warn("The dialog wrapper for " + dialogWrapper.getTitle() + " is already disposed");
        return;
      }
      super.show();
    }

    private void logMonitorConfiguration() {
      var ideFrame = WindowManager.getInstance().getFrame(CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this)));
      for (String message : ScreenUtil.loggableMonitorConfiguration(ideFrame)) {
        LOG.debug(message);
      }
    }

    private @Nullable Project guessProjectDependingOnKey(String key) {
      return !key.startsWith(WindowStateService.USE_APPLICATION_WIDE_STORE_KEY_PREFIX) ?
             CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this)) : null;
    }

    private static void maximize(@NotNull Dimension size, @Nullable Dimension alternativeSize) {
      if (alternativeSize != null) {
        size.width = Math.max(size.width, alternativeSize.width);
        size.height = Math.max(size.height, alternativeSize.height);
      }
    }

    private static @Nullable Dimension getSizeForTableContainer(@Nullable Component component) {
      if (component == null) return null;
      JBIterable<JTable> tables = UIUtil.uiTraverser(component).filter(JTable.class);
      if (!tables.isNotEmpty()) return null;
      Dimension size = component.getPreferredSize();
      for (JTable table : tables) {
        Dimension tableSize = table.getPreferredSize();
        size.width = Math.max(size.width, tableSize.width);
        size.height = Math.max(size.height, tableSize.height + size.height - table.getParent().getHeight());
      }
      size.width = Math.min(1000, Math.max(600, size.width));
      size.height = Math.min(800, size.height);
      return size;
    }

    private @Nullable Project getProject() {
      return SoftReference.dereference(myProject);
    }

    @Override
    public @NotNull IdeFocusManager getFocusManager() {
      Project project = getProject();
      if (project != null && !project.isDisposed()) {
        return IdeFocusManager.getInstance(project);
      }
      else {
        return IdeFocusManager.findInstance();
      }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void hide() {
      ThreadContext.resetThreadContext(() -> {
        super.hide();
        return null;
      });
    }

    @Override
    public void dispose() {
      if (isShowing()) {
        hide();
      }

      if (myWindowListener != null) {
        myWindowListener.saveSize();
        removeWindowListener(myWindowListener);
        removeWindowFocusListener(myWindowListener);
        myWindowListener = null;
      }
      DialogWrapper wrapper = getDialogWrapper();
      if (wrapper != null) wrapper.disposeIfNeeded();

      final BufferStrategy strategy = getBufferStrategy();
      if (strategy != null) {
        if (!EDT.isCurrentThreadEdt()) {
          LOG.error("Component dispose must be called on EDT", new Throwable());
        }
        strategy.dispose();
      }
      super.dispose();

      removeAll();
      DialogWrapper.cleanupRootPane(rootPane);
      DialogWrapper.cleanupWindowListeners(this);
      rootPane = null;
      myDisposed = true;

    }

    @Override
    public Component getMostRecentFocusOwner() {
      if (!myOpened) {
        final DialogWrapper wrapper = getDialogWrapper();
        if (wrapper != null) {
          JComponent toFocus = wrapper.getPreferredFocusedComponent();
          if (toFocus != null) {
            return toFocus;
          }
        }
      }
      return super.getMostRecentFocusOwner();
    }

    @Override
    public void paint(Graphics g) {
      if (!SystemInfoRt.isMac) {  // avoid rendering problems with non-aqua (alloy) LaFs under mac
        // actually, it's a bad idea to globally enable this for dialog graphics since renderers, for example, may not
        // inherit graphics so rendering hints won't be applied and trees or lists may render ugly.
        UISettings.setupAntialiasing(g);
      }
      if (!EDT.isCurrentThreadEdt()) {
        LOG.error("paint must be called on EDT", new Throwable());
      }

      super.paint(g);
    }

    private final class MyWindowListener extends WindowAdapter {
      @Override
      public void windowClosing(WindowEvent e) {
        DialogWrapper dialogWrapper = getDialogWrapper();
        if (dialogWrapper.shouldCloseOnCross()) {
          dialogWrapper.doCancelAction(e);
        }
      }

      @Override
      public void windowClosed(WindowEvent e) {
        saveSize();
      }

      public void saveSize() {
        if (myDimensionServiceKey != null &&
            myInitialSize != null &&
            myOpened) { // myInitialSize can be null only if dialog is disposed before first showing
          if (LOG.isDebugEnabled()) {
            LOG.debug("Saving the bounds of a dialog titled '" + getTitle() + "', the monitor configuration is:");
            logMonitorConfiguration();
          }
          final Project projectGuess = guessProjectDependingOnKey(myDimensionServiceKey);
          // Save location
          Point location = getLocation();
          getWindowStateService(projectGuess).putLocation(myDimensionServiceKey, location);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Saved location: " + location);
          }
          // Save size
          Dimension size = getSize();
          if (!myInitialSize.equals(size)) {
            getWindowStateService(projectGuess).putSize(myDimensionServiceKey, size);
            if (LOG.isDebugEnabled()) {
              LOG.debug("Saved size: " + size + " (the initial size was " + myInitialSize + ")");
            }
          }
          else if (LOG.isDebugEnabled()) {
            LOG.debug("Didn't save size because it's the same as the initial size: " + size);
          }
          myOpened = false;
        }
      }

      @Override
      public void windowGainedFocus(final WindowEvent e) {
        removeWindowFocusListener(this); // run this code only the first time our dialog is focused

        myOpened = true;

        DialogWrapper wrapper = getDialogWrapper();
        JComponent toFocus = wrapper == null ? null : wrapper.getPreferredFocusedComponent();
        JRootPane pane = getRootPane();
        if (pane != null && toFocus == null) {
          toFocus = pane.getDefaultButton();
        }

        if (pane != null) {
          IJSwingUtilities.moveMousePointerOn(pane.getDefaultButton());
        }

        setupSelectionOnPreferredComponent(toFocus);

        if (toFocus != null && toFocus.isEnabled()) {
          if (isShowing() && (!LoadingState.COMPONENTS_REGISTERED.isOccurred() ||
                              ApplicationManager.getApplication() == null ||
                              ApplicationManager.getApplication().isActive())) {
            toFocus.requestFocus();
          }
          else {
            toFocus.requestFocusInWindow();
          }
        }
      }
    }

    private final class MacDirtySizeHack extends ComponentAdapter {
      private int width;
      private int height;

      MacDirtySizeHack(int width, int height) {
        this.width = width;
        this.height = height;
      }

      void updateSize(int width, int height) {
        this.width = width;
        this.height = height;
        if (LOG.isDebugEnabled()) {
          LOG.debug("Manually set size for the mac hack: width = " + width + ", height = " + height);
        }
      }

      Dimension getSize() {
        return new Dimension(width, height);
      }

      @Override
      public void componentShown(ComponentEvent e) {
        removeComponentListener(this);
        myMacDirtySizeHack = null;
        int width = getWidth();
        int height = getHeight();
        if (width != this.width || height != this.height) {
          LOG.warn("The size of the dialog '" + getTitle() + "' has changed after showing, " +
                   "set width = " + this.width + ", height = " + this.height + ", " +
                   "now width = " + width + ", height = " + height + ", " +
                   "resetting it back");
          setSize(this.width, this.height);
        }
      }
    }

    private final class DialogRootPane extends JRootPane implements UiDataProvider {

      private final boolean myGlassPaneIsSet;

      private Dimension myLastMinimumSize;

      private DialogRootPane() {
        setGlassPane(new IdeGlassPaneImpl(this));
        myGlassPaneIsSet = true;
        putClientProperty("DIALOG_ROOT_PANE", true);
        setBorder(JBUI.CurrentTheme.Window.getBorder(isUndecorated()));
      }

      @Override
      protected @NotNull JLayeredPane createLayeredPane() {
        JLayeredPane p = new JBLayeredPane() {
          @Override
          protected Graphics getComponentGraphics(Graphics g) {
            return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g));
          }
        };
        p.setName(this.getName()+".layeredPane");
        return p;
      }

      @Override
      public void validate() {
        super.validate();
        DialogWrapper wrapper = myDialogWrapper.get();
        if (wrapper != null && wrapper.isAutoAdjustable()) {
          Window window = wrapper.getWindow();
          if (window != null) {
            Dimension size = getMinimumSize();
            if (!(Objects.equals(size, myLastMinimumSize))) {
              // update window minimum size only if root pane minimum size is changed
              if (size == null) {
                myLastMinimumSize = null;
              }
              else {
                myLastMinimumSize = new Dimension(size);
                JBInsets.addTo(size, window.getInsets());
                Rectangle screen = ScreenUtil.getScreenRectangle(window);
                if (size.width > screen.width || size.height > screen.height) {
                  Application application = ApplicationManager.getApplication();
                  if (application != null && application.isInternal()) {
                    StringBuilder sb = new StringBuilder("dialog minimum size is bigger than screen: ");
                    sb.append(size.width).append("x").append(size.height);
                    IJSwingUtilities.appendComponentClassNames(sb, this);
                    LOG.warn(sb.toString());
                  }
                  if (size.width > screen.width) size.width = screen.width;
                  if (size.height > screen.height) size.height = screen.height;
                }
              }
              window.setMinimumSize(size);
            }
          }
        }
      }

      @Override
      public void setGlassPane(final Component glass) {
        if (myGlassPaneIsSet) {
          LOG.warn("Setting of glass pane for DialogWrapper is prohibited", new Exception());
          return;
        }

        super.setGlassPane(glass);
      }

      @Override
      public void setContentPane(Container contentPane) {
        super.setContentPane(contentPane);
        if (contentPane != null) {
          contentPane.addMouseMotionListener(new MouseMotionAdapter() {}); // listen to mouse motino events for a11y
        }
      }

      @Override
      public void uiDataSnapshot(@NotNull DataSink sink) {
        DialogWrapper wrapper = myDialogWrapper.get();
        if (wrapper == null) return;
        sink.set(PlatformDataKeys.UI_DISPOSABLE, wrapper.getDisposable());
      }
    }

    private static @NotNull WindowStateService getWindowStateService(@Nullable Project project) {
      return project == null ? WindowStateService.getInstance() : WindowStateService.getInstance(project);
    }
  }

  private static void setupSelectionOnPreferredComponent(final JComponent component) {
    if (component instanceof JTextField field) {
      String text = field.getText();
      if (text != null && field.getClientProperty(HAVE_INITIAL_SELECTION) == null) {
        field.setSelectionStart(0);
        field.setSelectionEnd(text.length());
      }
    }
    else if (component instanceof JComboBox) {
      ((JComboBox<?>)component).getEditor().selectAll();
    }
  }

  @Override
  public void setContentPane(JComponent content) {
    boolean undecorated = myDialog.getWindow() != null && myDialog.getWindow().isUndecorated();
    myDialog.setContentPane(IdeFrameDecorator.Companion.isCustomDecorationActive() && !undecorated && !isHeadlessEnv()
                            ? CustomFrameDialogContent.Companion.getCustomContentHolder(getWindow(), content, false)
                            : content);
  }

  @Override
  public void centerInParent() {
    myDialog.centerInParent();
  }

  public void setAutoRequestFocus(boolean b) {
    UIUtil.setAutoRequestFocus((JDialog)myDialog, b);
  }
  
}
