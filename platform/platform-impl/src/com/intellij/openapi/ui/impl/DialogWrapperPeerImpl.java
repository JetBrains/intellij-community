/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.ui.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.reference.SoftReference;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.OwnerOptional;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
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

public class DialogWrapperPeerImpl extends DialogWrapperPeer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.DialogWrapper");

  public static boolean isHeadlessEnv() {
    Application app = ApplicationManager.getApplication();
    return app == null ? GraphicsEnvironment.isHeadless() : app.isUnitTestMode() || app.isHeadlessEnvironment();
  }

  private final DialogWrapper myWrapper;
  private final AbstractDialog myDialog;
  private final boolean myCanBeParent;
  private final WindowManagerEx myWindowManager;
  private final List<Runnable> myDisposeActions = new ArrayList<>();
  private Project myProject;
  private ActionCallback myTypeAheadCallback;

  protected DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper, @Nullable Project project, boolean canBeParent, @NotNull DialogWrapper.IdeModalityType ideModalityType) {
    boolean headless = isHeadlessEnv();
    myWrapper = wrapper;
    myTypeAheadCallback = myWrapper.isTypeAheadEnabled() ? new ActionCallback() : null;
    myWindowManager = getWindowManager();

    Window window = null;
    if (myWindowManager != null) {
      if (project == null) {
        //noinspection deprecation
        project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
      }

      myProject = project;

      window = myWindowManager.suggestParentWindow(project);
      if (window == null) {
        Window focusedWindow = myWindowManager.getMostRecentFocusedWindow();
        if (focusedWindow instanceof IdeFrameImpl) {
          window = focusedWindow;
        }
      }
      if (window == null) {
        IdeFrame[] frames = myWindowManager.getAllProjectFrames();
        for (IdeFrame frame : frames) {
          if (frame instanceof IdeFrameImpl && ((IdeFrameImpl)frame).isActive()) {
            window = (IdeFrameImpl)frame;
            break;
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

    myDialog = createDialog(headless, owner, wrapper, myProject, myTypeAheadCallback, ideModalityType);
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
   * @param parent parent component which is used to calculate heavy weight window ancestor.
   *               {@code parent} cannot be {@code null} and must be showing.
   */
  protected DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper, @NotNull Component parent, boolean canBeParent) {
    boolean headless = isHeadlessEnv();
    myWrapper = wrapper;
    myWindowManager = getWindowManager();
    myDialog = createDialog(headless, OwnerOptional.fromComponent(parent).get(), wrapper, null, null, DialogWrapper.IdeModalityType.IDE);
    myCanBeParent = headless || canBeParent;
  }

  protected DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper, Window owner, boolean canBeParent, DialogWrapper.IdeModalityType ideModalityType) {
    boolean headless = isHeadlessEnv();
    myWrapper = wrapper;
    myWindowManager = getWindowManager();
    myDialog = createDialog(headless, owner, wrapper, null, null, DialogWrapper.IdeModalityType.IDE);
    myCanBeParent = headless || canBeParent;

    if (!headless) {
      Dialog.ModalityType modalityType = DialogWrapper.IdeModalityType.IDE.toAwtModality();
      if (Registry.is("ide.perProjectModality")) {
        modalityType = ideModalityType.toAwtModality();
      }
      myDialog.setModalityType(modalityType);
    }
  }

  private static WindowManagerEx getWindowManager() {
    WindowManagerEx windowManager = null;
    Application application = ApplicationManager.getApplication();
    if (application != null && application.hasComponent(WindowManager.class)) {
      windowManager = (WindowManagerEx)WindowManager.getInstance();
    }
    return windowManager;
  }

  private static AbstractDialog createDialog(boolean headless,
                                             Window owner,
                                             DialogWrapper wrapper,
                                             Project project,
                                             ActionCallback typeAhead,
                                             DialogWrapper.IdeModalityType ideModalityType) {
    if (headless) {
      return new HeadlessDialog(wrapper);
    }
    else {
      ActionCallback focused = new ActionCallback("DialogFocusedCallback");
      ActionCallback typeAheadDone = new ActionCallback("DialogTypeAheadDone");
      MyDialog dialog = new MyDialog(owner, wrapper, project, focused, typeAheadDone, typeAhead);
      dialog.setModalityType(ideModalityType.toAwtModality());
      return dialog;
    }
  }

  @Override
  public boolean isHeadless() {
    return myDialog instanceof HeadlessDialog;
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
  @SuppressWarnings("SSBasedInspection")
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

    UIUtil.invokeLaterIfNeeded(disposer);
  }

  private boolean isProgressDialog() {
    return myWrapper.isModalProgress();
  }

  @Override
  @Nullable
  public Container getContentPane() {
    return getRootPane() != null ? myDialog.getContentPane() : null;
  }

  /**
   * @see javax.swing.JDialog#validate
   */
  @Override
  public void validate() {
    myDialog.validate();
  }

  /**
   * @see javax.swing.JDialog#repaint
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
   * @see java.awt.Window#pack
   */
  @Override
  public void pack() {
    myDialog.pack();
  }

  @Override
  public void setAppIcons() {
    AppUIUtil.updateWindowIcon(getWindow());
  }

  @Override
  public Dimension getPreferredSize() {
    return myDialog.getPreferredSize();
  }

  @Override
  public void setModal(boolean modal) {
    myDialog.setModal(modal);
  }

  @Override
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
  public void isResizable() {
    myDialog.isResizable();
  }

  @Override
  public void setResizable(boolean resizable) {
    myDialog.setResizable(resizable);
  }

  @NotNull
  @Override
  public Point getLocation() {
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
  public ActionCallback show() {
    LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");
    if (myTypeAheadCallback != null) {
      IdeFocusManager.getInstance(myProject).typeAheadUntil(myTypeAheadCallback);
    }                         LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");
    final ActionCallback result = new ActionCallback();

    final AnCancelAction anCancelAction = new AnCancelAction();
    final JRootPane rootPane = getRootPane();
    UIUtil.decorateFrame(rootPane);
    anCancelAction.registerCustomShortcutSet(CommonShortcuts.ESCAPE, rootPane);
    myDisposeActions.add(() -> anCancelAction.unregisterCustomShortcutSet(rootPane));

    if (!myCanBeParent && myWindowManager != null) {
      myWindowManager.doNotSuggestAsParent(myDialog.getWindow());
    }

    final CommandProcessorEx commandProcessor =
      ApplicationManager.getApplication() != null ? (CommandProcessorEx)CommandProcessor.getInstance() : null;
    final boolean appStarted = commandProcessor != null;

    boolean changeModalityState = appStarted && myDialog.isModal()
                                  && !isProgressDialog(); // ProgressWindow starts a modality state itself
    Project project = myProject;

    boolean perProjectModality = Registry.is("ide.perProjectModality");
    if (changeModalityState) {
      commandProcessor.enterModal();
      if (perProjectModality) {
        LaterInvocator.enterModal(project, myDialog.getWindow());
      } else {
        LaterInvocator.enterModal(myDialog);
      }
    }

    if (appStarted) {
      hidePopupsIfNeeded();
    }

    myDialog.getWindow().setAutoRequestFocus(true);

    try {
      myDialog.show();
    }
    finally {
      if (changeModalityState) {
        commandProcessor.leaveModal();
        if (perProjectModality) {
          LaterInvocator.leaveModal(project, myDialog.getWindow());
        } else {
          LaterInvocator.leaveModal(myDialog);
        }
      }

      myDialog.getFocusManager().doWhenFocusSettlesDown(result.createSetDoneRunnable());
    }

    return result;
  }

  //hopefully this whole code will go away
  private void hidePopupsIfNeeded() {
    if (!SystemInfo.isMac) return;

    StackingPopupDispatcher.getInstance().hidePersistentPopups();
    myDisposeActions.add(() -> StackingPopupDispatcher.getInstance().restorePersistentPopups());
  }

  private class AnCancelAction extends AnAction implements DumbAware {

    @Override
    public void update(AnActionEvent e) {
      Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      e.getPresentation().setEnabled(false);
      if (focusOwner instanceof JComponent && SpeedSearchBase.hasActiveSpeedSearch((JComponent)focusOwner)) {
        return;
      }

      if (StackingPopupDispatcher.getInstance().isPopupFocused()) return;
      JTree tree = UIUtil.getParentOfType(JTree.class, focusOwner);
      JTable table = UIUtil.getParentOfType(JTable.class, focusOwner);

      if (tree != null || table != null) {
        if (hasNoEditingTreesOrTablesUpward(focusOwner)) {
          e.getPresentation().setEnabled(true);
        }
      }
    }

    private boolean hasNoEditingTreesOrTablesUpward(Component comp) {
      while (comp != null) {
        if (isEditingTreeOrTable(comp)) return false;
        comp = comp.getParent();
      }
      return true;
    }

    private boolean isEditingTreeOrTable(Component comp) {
      if (comp instanceof JTree) {
        return ((JTree)comp).isEditing();
      }
      else if (comp instanceof JTable) {
        return ((JTable)comp).isEditing();
      }
      return false;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myWrapper.doCancelAction(e.getInputEvent());
    }
  }


  private static class MyDialog extends JDialog implements DialogWrapperDialog, DataProvider, Queryable, AbstractDialog {
    private final WeakReference<DialogWrapper> myDialogWrapper;

    /**
     * Initial size of the dialog. When the dialog is being closed and
     * current size of the dialog is not equals to the initial size then the
     * current (changed) size is stored in the {@code DimensionService}.
     */
    private Dimension myInitialSize;
    private String myDimensionServiceKey;
    private boolean myOpened = false;
    private boolean myActivated = false;

    private MyDialog.MyWindowListener myWindowListener;

    private final WeakReference<Project> myProject;
    private final ActionCallback myFocusedCallback;
    private final ActionCallback myTypeAheadDone;
    private final ActionCallback myTypeAheadCallback;

    public MyDialog(Window owner,
                    DialogWrapper dialogWrapper,
                    Project project,
                    @NotNull ActionCallback focused,
                    @NotNull ActionCallback typeAheadDone,
                    ActionCallback typeAheadCallback) {
      super(owner);
      myDialogWrapper = new WeakReference<>(dialogWrapper);
      myProject = project != null ? new WeakReference<>(project) : null;

      setFocusTraversalPolicy(new LayoutFocusTraversalPolicyExt() {
        @Override
        protected boolean accept(Component aComponent) {
          if (UIUtil.isFocusProxy(aComponent)) return false;
          return super.accept(aComponent);
        }
      });

      myFocusedCallback = focused;
      myTypeAheadDone = typeAheadDone;
      myTypeAheadCallback = typeAheadCallback;

      final long typeAhead = getDialogWrapper().getTypeAheadTimeoutMs();
      if (typeAhead <= 0) {
        myTypeAheadDone.setDone();
      }

      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      myWindowListener = new MyWindowListener();
      addWindowListener(myWindowListener);
      UIUtil.setAutoRequestFocus(this, true);
    }

    @Override
    public JDialog getWindow() {
      return this;
    }

    @Override
    public void putInfo(@NotNull Map<String, String> info) {
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
    public Object getData(String dataId) {
      final DialogWrapper wrapper = myDialogWrapper.get();
      if (wrapper instanceof DataProvider) {
        return ((DataProvider)wrapper).getData(dataId);
      }
      if (wrapper instanceof TypeSafeDataProvider) {
        TypeSafeDataProviderAdapter adapter = new TypeSafeDataProviderAdapter((TypeSafeDataProvider)wrapper);
        return adapter.getData(dataId);
      }
      return null;
    }

    @Override
    public void setSize(int width, int height) {
      _setSizeForLocation(width, height, null);
    }

    private void _setSizeForLocation(int width, int height, @Nullable Point initial) {
      Point location = initial != null ? initial : getLocation();
      Rectangle rect = new Rectangle(location.x, location.y, width, height);
      ScreenUtil.fitToScreen(rect);
      if (initial != null || location.x != rect.x || location.y != rect.y) {
        setLocation(rect.x, rect.y);
      }

      super.setSize(rect.width, rect.height);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
      Rectangle rect = new Rectangle(x, y, width, height);
      ScreenUtil.fitToScreen(rect);
      super.setBounds(rect.x, rect.y, rect.width, rect.height);
    }

    @Override
    public void setBounds(Rectangle r) {
      ScreenUtil.fitToScreen(r);
      super.setBounds(r);
    }

    @NotNull
    @Override
    protected JRootPane createRootPane() {
      return new DialogRootPane();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void show() {

      final DialogWrapper dialogWrapper = getDialogWrapper();
      boolean isAutoAdjustable = dialogWrapper.isAutoAdjustable();
      Point location = null;
      if (isAutoAdjustable) {
        pack();

        Dimension packedSize = getSize();
        Dimension minSize = getMinimumSize();
        setSize(Math.max(packedSize.width, minSize.width), Math.max(packedSize.height, minSize.height));

        setSize((int)(getWidth() * dialogWrapper.getHorizontalStretch()), (int)(getHeight() * dialogWrapper.getVerticalStretch()));

        // Restore dialog's size and location

        myDimensionServiceKey = dialogWrapper.getDimensionKey();

        if (myDimensionServiceKey != null) {
          final Project projectGuess = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this));
          location = DimensionService.getInstance().getLocation(myDimensionServiceKey, projectGuess);
          Dimension size = DimensionService.getInstance().getSize(myDimensionServiceKey, projectGuess);
          if (size != null) {
            myInitialSize = new Dimension(size);
            _setSizeForLocation(myInitialSize.width, myInitialSize.height, location);
          }
        }

        if (myInitialSize == null) {
          myInitialSize = getSize();
        }
      }

      if (location == null) {
        location = dialogWrapper.getInitialLocation();
      }

      if (location != null) {
        setLocation(location);
      }
      else {
        setLocationRelativeTo(getOwner());
      }

      if (isAutoAdjustable) {
        final Rectangle bounds = getBounds();
        ScreenUtil.fitToScreen(bounds);
        setBounds(bounds);
      }

      if (Registry.is("actionSystem.fixLostTyping")) {
        final IdeEventQueue queue = IdeEventQueue.getInstance();
        if (queue != null) {
          queue.getKeyEventDispatcher().resetState();
        }

      }

      // Workaround for switching workspaces on dialog show
      if (SystemInfo.isMac && myProject != null && Registry.is("ide.mac.fix.dialog.showing") && !dialogWrapper.isModalProgress()) {
        final IdeFrame frame = WindowManager.getInstance().getIdeFrame(myProject.get());
        AppIcon.getInstance().requestFocus(frame);
      }

      setBackground(UIUtil.getPanelBackground());

      final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      if (app != null && !app.isLoaded() && Splash.BOUNDS != null) {
        final Point loc = getLocation();
        loc.y = Splash.BOUNDS.y + Splash.BOUNDS.height;
        setLocation(loc);
      }
      super.show();
    }

    @Nullable
    private Project getProject() {
      return SoftReference.dereference(myProject);
    }

    @NotNull
    @Override
    public IdeFocusManager getFocusManager() {
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
      super.hide();
    }

    @Override
    public void dispose() {
      if (isShowing()) {
        hide();
      }

      if (myWindowListener != null) {
        myWindowListener.saveSize();
        removeWindowListener(myWindowListener);
        myWindowListener = null;
      }

      DialogWrapper.cleanupWindowListeners(this);

      final BufferStrategy strategy = getBufferStrategy();
      if (strategy != null) {
        strategy.dispose();
      }
      super.dispose();

      removeAll();
      DialogWrapper.cleanupRootPane(rootPane);
      rootPane = null;

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
      if (!SystemInfo.isMac || UIUtil.isUnderAquaLookAndFeel()) {  // avoid rendering problems with non-aqua (alloy) LaFs under mac
        // actually, it's a bad idea to globally enable this for dialog graphics since renderers, for example, may not
        // inherit graphics so rendering hints won't be applied and £trees or lists may render ugly.
        UISettings.setupAntialiasing(g);
      }

      super.paint(g);
    }

    @SuppressWarnings("SSBasedInspection")
    private class MyWindowListener extends WindowAdapter {
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
          final Project projectGuess = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(MyDialog.this));

          // Save location
          Point location = getLocation();
          DimensionService.getInstance().setLocation(myDimensionServiceKey, location, projectGuess);
          // Save size
          Dimension size = getSize();
          if (!myInitialSize.equals(size)) {
            DimensionService.getInstance().setSize(myDimensionServiceKey, size, projectGuess);
          }
          myOpened = false;
        }
      }

      @Override
      public void windowOpened(final WindowEvent e) {
        if (SystemInfo.isMacOSLion) {
          Window window = e.getWindow();
          if (window instanceof Dialog) {
            ID _native = MacUtil.findWindowForTitle(((Dialog)window).getTitle());
            if (_native != null && _native.intValue() > 0) {
              // see MacMainFrameDecorator
              // NSCollectionBehaviorFullScreenAuxiliary = 1 << 8
              Foundation.invoke(_native, "setCollectionBehavior:", 1 << 8);
            }
          }
        }
        SwingUtilities.invokeLater(() -> {
          myOpened = true;
          final DialogWrapper activeWrapper = getActiveWrapper();
          for (JComponent c : UIUtil.uiTraverser(e.getWindow()).filter(JComponent.class)) {
            GraphicsUtil.setAntialiasingType(c, AntialiasingType.getAAHintForSwingComponent());
          }
          if (activeWrapper == null) {
            myFocusedCallback.setRejected();
            myTypeAheadDone.setRejected();
          }
        });
      }

      @Override
      public void windowActivated(final WindowEvent e) {
        SwingUtilities.invokeLater(() -> {
          final DialogWrapper wrapper = getActiveWrapper();
          if (wrapper == null && !myFocusedCallback.isProcessed()) {
            myFocusedCallback.setRejected();
            myTypeAheadDone.setRejected();
            return;
          }

          if (myActivated) {
            return;
          }
          myActivated = true;
          JComponent toFocus = wrapper == null ? null : wrapper.getPreferredFocusedComponent();
          if (getRootPane() != null && toFocus == null) {
            toFocus = getRootPane().getDefaultButton();
          }

          if (getRootPane() != null) {
            IJSwingUtilities.moveMousePointerOn(getRootPane().getDefaultButton());
          }
          setupSelectionOnPreferredComponent(toFocus);

          if (toFocus != null) {
            if (isShowing() && isActive()) {
             toFocus.requestFocus();
              notifyFocused(wrapper);
            }
          } else {
            if (isShowing()) {
              notifyFocused(wrapper);
            }
          }
          if (myTypeAheadCallback != null) {
            myTypeAheadCallback.setDone();
          }
        });
      }

      private void notifyFocused(DialogWrapper wrapper) {
        myFocusedCallback.setDone();
        final long timeout = wrapper.getTypeAheadTimeoutMs();
        if (timeout > 0) {
          SimpleTimer.getInstance().setUp(new EdtRunnable() {
            @Override
            public void runEdt() {
              myTypeAheadDone.setDone();
            }
          }, timeout);
        }
      }

      private DialogWrapper getActiveWrapper() {
        DialogWrapper activeWrapper = getDialogWrapper();
        if (activeWrapper == null || !activeWrapper.isShowing()) {
          return null;
        }

        return activeWrapper;
      }
    }

    private class DialogRootPane extends JRootPane implements DataProvider {

      private final boolean myGlassPaneIsSet;

      private Dimension myLastMinimumSize;

      private DialogRootPane() {
        setGlassPane(new IdeGlassPaneImpl(this));
        myGlassPaneIsSet = true;
        putClientProperty("DIALOG_ROOT_PANE", true);
      }

      @NotNull
      @Override
      protected JLayeredPane createLayeredPane() {
        JLayeredPane p = new JBLayeredPane();
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
            if (!(size == null ? myLastMinimumSize == null : size.equals(myLastMinimumSize))) {
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
                    LOG.warn("minimum size " + size.width + "x" + size.height +
                             " is bigger than screen " + screen.width + "x" + screen.height);
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
      public Object getData(@NonNls String dataId) {
        final DialogWrapper wrapper = myDialogWrapper.get();
        return wrapper != null && PlatformDataKeys.UI_DISPOSABLE.is(dataId) ? wrapper.getDisposable() : null;
      }
    }
  }

  private static void setupSelectionOnPreferredComponent(final JComponent component) {
    if (component instanceof JTextField) {
      JTextField field = (JTextField)component;
      String text = field.getText();
      if (text != null && field.getClientProperty(HAVE_INITIAL_SELECTION) == null) {
        field.setSelectionStart(0);
        field.setSelectionEnd(text.length());
      }
    }
    else if (component instanceof JComboBox) {
      JComboBox combobox = (JComboBox)component;
      combobox.getEditor().selectAll();
    }
  }

  @Override
  public void setContentPane(JComponent content) {
    myDialog.setContentPane(content);
  }

  @Override
  public void centerInParent() {
    myDialog.centerInParent();
  }

  public void setAutoRequestFocus(boolean b) {
    UIUtil.setAutoRequestFocus((JDialog)myDialog, b);
  }
}
