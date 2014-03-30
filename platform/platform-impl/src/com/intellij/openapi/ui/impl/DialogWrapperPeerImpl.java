/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.ui.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
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
import com.intellij.openapi.wm.*;
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
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferStrategy;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DialogWrapperPeerImpl extends DialogWrapperPeer implements FocusTrackbackProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.DialogWrapper");

  private final DialogWrapper myWrapper;
  private AbstractDialog myDialog;
  private boolean myCanBeParent = true;
  private WindowManagerEx myWindowManager;
  private final List<Runnable> myDisposeActions = new ArrayList<Runnable>();
  private Project myProject;

  private final ActionCallback myWindowFocusedCallback = new ActionCallback("DialogFocusedCallback");
  private final ActionCallback myTypeAheadDone = new ActionCallback("DialogTypeAheadDone");
  private ActionCallback myTypeAheadCallback;

  protected DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper, @Nullable Project project, boolean canBeParent, DialogWrapper.IdeModalityType ideModalityType) {
    myWrapper = wrapper;
    myTypeAheadCallback = myWrapper.isTypeAheadEnabled() ? new ActionCallback() : null;
    myWindowManager = null;
    Application application = ApplicationManager.getApplication();
    if (application != null && application.hasComponent(WindowManager.class)) {
      myWindowManager = (WindowManagerEx)WindowManager.getInstance();
    }

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
    else {
      if (!isHeadless()) {
        owner = JOptionPane.getRootFrame();
      } else {
        owner = null;
      }
    }

    createDialog(owner, canBeParent, ideModalityType);
  }

  /**
   * Creates modal <code>DialogWrapper</code>. The currently active window will be the dialog's parent.
   *
   * @param project     parent window for the dialog will be calculated based on focused window for the
   *                    specified <code>project</code>. This parameter can be <code>null</code>. In this case parent window
   *                    will be suggested based on current focused window.
   * @param canBeParent specifies whether the dialog can be parent for other windows. This parameter is used
   *                    by <code>WindowManager</code>.
   */
  protected DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper, @Nullable Project project, boolean canBeParent) {
    this(wrapper, project, canBeParent, DialogWrapper.IdeModalityType.IDE);
  }

  protected DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper, boolean canBeParent) {
    this(wrapper, (Project)null, canBeParent);
  }

  @Override
  public boolean isHeadless() {
    return isHeadlessEnv();
  }

  @Override
  public Object[] getCurrentModalEntities() {
    return LaterInvocator.getCurrentModalEntities();
  }

  public static boolean isHeadlessEnv() {
    Application app = ApplicationManager.getApplication();
    if (app == null) return GraphicsEnvironment.isHeadless();

    return app.isUnitTestMode() || app.isHeadlessEnvironment();
  }

  /**
   * @param parent parent component which is used to calculate heavy weight window ancestor.
   *               <code>parent</code> cannot be <code>null</code> and must be showing.
   */
  protected DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper, @NotNull Component parent, boolean canBeParent) {
    myWrapper = wrapper;
    if (!parent.isShowing() && parent != JOptionPane.getRootFrame()) {
      throw new IllegalArgumentException("parent must be showing: " + parent);
    }
    myWindowManager = null;
    Application application = ApplicationManager.getApplication();
    if (application != null && application.hasComponent(WindowManager.class)) {
      myWindowManager = (WindowManagerEx)WindowManager.getInstance();
    }

    Window owner = parent instanceof Window ? (Window)parent : (Window)SwingUtilities.getAncestorOfClass(Window.class, parent);
    if (!(owner instanceof Dialog) && !(owner instanceof Frame)) {
      owner = JOptionPane.getRootFrame();
    }
    createDialog(owner, canBeParent);
  }

  public DialogWrapperPeerImpl(@NotNull final DialogWrapper wrapper,final Window owner, final boolean canBeParent,
                               final DialogWrapper.IdeModalityType ideModalityType ) {
    myWrapper = wrapper;
    myWindowManager = null;
    Application application = ApplicationManager.getApplication();
    if (application != null && application.hasComponent(WindowManager.class)) {
      myWindowManager = (WindowManagerEx)WindowManager.getInstance();
    }
    createDialog(owner, canBeParent);

    if (!isHeadless()) {
      Dialog.ModalityType modalityType = DialogWrapper.IdeModalityType.IDE.toAwtModality();
      if (Registry.is("ide.perProjectModality")) {
        modalityType = ideModalityType.toAwtModality();
      }
      myDialog.setModalityType(modalityType);
    }
  }

  /** @see DialogWrapper#DialogWrapper(boolean, boolean)
   */
  @Deprecated
  public DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper, final boolean canBeParent, final boolean applicationModalIfPossible) {
    this(wrapper, null, canBeParent, applicationModalIfPossible);
  }

  @Deprecated
  public DialogWrapperPeerImpl(@NotNull DialogWrapper wrapper,final Window owner, final boolean canBeParent, final boolean applicationModalIfPossible) {
      this(wrapper, owner, canBeParent, applicationModalIfPossible ? DialogWrapper.IdeModalityType.IDE : DialogWrapper.IdeModalityType.PROJECT);
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

  private void createDialog(@Nullable Window owner, boolean canBeParent, DialogWrapper.IdeModalityType ideModalityType) {
    if (isHeadless()) {
      myDialog = new HeadlessDialog();
      return;
    }

    myDialog = new MyDialog(owner, myWrapper, myProject, myWindowFocusedCallback, myTypeAheadDone, myTypeAheadCallback);
    myDialog.setModalityType(ideModalityType.toAwtModality());

    myCanBeParent = canBeParent;
  }

  private void createDialog(@Nullable Window owner, boolean canBeParent) {
    createDialog(owner, canBeParent, DialogWrapper.IdeModalityType.IDE);
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
    final JRootPane root = myDialog.getRootPane();

    Runnable disposer = new Runnable() {
      @Override
      public void run() {
        myDialog.dispose();
        myProject = null;

        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            if (myDialog != null && root != null) {
              myDialog.remove(root);
            }
          }
        });
      }
    };

    if (EventQueue.isDispatchThread()) {
      disposer.run();
    }
    else {
      SwingUtilities.invokeLater(disposer);
    }
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

  @Override
  public Point getLocation() {
    return myDialog.getLocation();
  }

  @Override
  public void setLocation(Point p) {
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
    anCancelAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)), rootPane);
    myDisposeActions.add(new Runnable() {
      @Override
      public void run() {
        anCancelAction.unregisterCustomShortcutSet(rootPane);
      }
    });

    if (!myCanBeParent && myWindowManager != null) {
      myWindowManager.doNotSuggestAsParent(myDialog.getWindow());
    }

    final CommandProcessorEx commandProcessor =
      ApplicationManager.getApplication() != null ? (CommandProcessorEx)CommandProcessor.getInstance() : null;
    final boolean appStarted = commandProcessor != null;

    if (myDialog.isModal() && !isProgressDialog()) {
      if (appStarted) {
        commandProcessor.enterModal();
        LaterInvocator.enterModal(myDialog);
      }
    }

    if (appStarted) {
      hidePopupsIfNeeded();
    }

    try {
      myDialog.show();
    }
    finally {
      if (myDialog.isModal() && !isProgressDialog()) {
        if (appStarted) {
          commandProcessor.leaveModal();
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
    myDisposeActions.add(new Runnable() {
      @Override
      public void run() {
        StackingPopupDispatcher.getInstance().restorePersistentPopups();
      }
    });
  }

  @Override
  public FocusTrackback getFocusTrackback() {
    return myDialog.getFocusTrackback();
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

      if (focusOwner instanceof JTree) {
        JTree tree = (JTree)focusOwner;
        if (!tree.isEditing()) {
          e.getPresentation().setEnabled(true);
        }
      }
      else if (focusOwner instanceof JTable) {
        JTable table = (JTable)focusOwner;
        if (!table.isEditing()) {
          e.getPresentation().setEnabled(true);
        }
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myWrapper.doCancelAction(e.getInputEvent());
    }
  }


  private static class MyDialog extends JDialog implements DialogWrapperDialog, DataProvider, FocusTrackback.Provider, Queryable, AbstractDialog {
    private final WeakReference<DialogWrapper> myDialogWrapper;

    /**
     * Initial size of the dialog. When the dialog is being closed and
     * current size of the dialog is not equals to the initial size then the
     * current (changed) size is stored in the <code>DimensionService</code>.
     */
    private Dimension myInitialSize;
    private String myDimensionServiceKey;
    private boolean myOpened = false;
    private boolean myActivated = false;

    private FocusTrackback myFocusTrackback;
    private MyDialog.MyWindowListener myWindowListener;

    private final WeakReference<Project> myProject;
    private final ActionCallback myFocusedCallback;
    private final ActionCallback myTypeAheadDone;
    private final ActionCallback myTypeAheadCallback;
    private MyComponentListener myComponentListener;

    public MyDialog(Window owner,
                    DialogWrapper dialogWrapper,
                    Project project,
                    @NotNull ActionCallback focused,
                    @NotNull ActionCallback typeAheadDone,
                    ActionCallback typeAheadCallback) {
      super(owner);
      myDialogWrapper = new WeakReference<DialogWrapper>(dialogWrapper);
      myProject = project != null ? new WeakReference<Project>(project) : null;

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

      myComponentListener = new MyComponentListener();
      addComponentListener(myComponentListener);
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
    public FocusTrackback getFocusTrackback() {
      return myFocusTrackback;
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
      else if (wrapper instanceof TypeSafeDataProvider) {
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

    @Override
    protected JRootPane createRootPane() {
      return new DialogRootPane();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void show() {
      myFocusTrackback = new FocusTrackback(getDialogWrapper(), getParent(), true);

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
      addWindowListener(new WindowAdapter() {
        @Override
        public void windowActivated(WindowEvent e) {
          final DialogWrapper wrapper = getDialogWrapper();
          if (wrapper != null && myFocusTrackback != null) {
            myFocusTrackback.cleanParentWindow();
            myFocusTrackback.registerFocusComponent(new FocusTrackback.ComponentQuery() {
              @Override
              public Component getComponent() {
                return wrapper.getPreferredFocusedComponent();
              }
            });
          }
        }

        @Override
        public void windowDeactivated(WindowEvent e) {
          if (!isModal()) {
            final Ref<IdeFocusManager> focusManager = new Ref<IdeFocusManager>(null);
            Project project = getProject();
            if (project != null && !project.isDisposed()) {
              focusManager.set(getFocusManager());
              focusManager.get().doWhenFocusSettlesDown(new Runnable() {
                @Override
                public void run() {
                  disposeFocusTrackbackIfNoChildWindowFocused(focusManager.get());
                }
              });
            }
            else {
              disposeFocusTrackbackIfNoChildWindowFocused(focusManager.get());
            }
          }
        }

        @Override
        public void windowOpened(WindowEvent e) {
          if (!SystemInfo.isMacOSLion) return;
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
      });

      if (Registry.is("actionSystem.fixLostTyping")) {
        final IdeEventQueue queue = IdeEventQueue.getInstance();
        if (queue != null) {
          queue.getKeyEventDispatcher().resetState();
        }

       // if (myProject != null) {
       //   Project project = myProject.get();
          //if (project != null && !project.isDisposed() && project.isInitialized()) {
          // // IdeFocusManager.findInstanceByComponent(this).requestFocus(new MyFocusCommand(dialogWrapper), true);
          //}
       // }
      }

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

    private void disposeFocusTrackbackIfNoChildWindowFocused(@Nullable IdeFocusManager focusManager) {
      if (myFocusTrackback == null) return;

      final DialogWrapper wrapper = myDialogWrapper.get();
      if (wrapper == null || !wrapper.isShowing()) {
        myFocusTrackback.dispose();
        return;
      }

      if (focusManager != null) {
        final Component c = focusManager.getFocusedDescendantFor(wrapper.getContentPane());
        if (c == null) {
          myFocusTrackback.dispose();
        }
      }
      else {
        final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner == null || !SwingUtilities.isDescendingFrom(owner, wrapper.getContentPane())) {
          myFocusTrackback.dispose();
        }
      }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void hide() {
      super.hide();
      if (myFocusTrackback != null && !(myFocusTrackback.isSheduledForRestore() || myFocusTrackback.isWillBeSheduledForRestore())) {
        myFocusTrackback.setWillBeSheduledForRestore();
        IdeFocusManager mgr = getFocusManager();
        Runnable r = new Runnable() {
          @Override
          public void run() {
            if (myFocusTrackback != null)  myFocusTrackback.restoreFocus();
            myFocusTrackback = null;
          }
        };
        mgr.doWhenFocusSettlesDown(r);
      }
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

      if (myComponentListener != null) {
        removeComponentListener(myComponentListener);
        myComponentListener = null;
      }

      if (myFocusTrackback != null && !(myFocusTrackback.isSheduledForRestore() || myFocusTrackback.isWillBeSheduledForRestore())) {
        myFocusTrackback.dispose();
        myFocusTrackback = null;
      }


      final BufferStrategy strategy = getBufferStrategy();
      if (strategy != null) {
        strategy.dispose();
      }
      super.dispose();


      if (rootPane != null) { // Workaround for bug in native code to hold rootPane
        try {
          Field field = rootPane.getClass().getDeclaredField("glassPane");
          field.setAccessible(true);
          field.set(rootPane, null);

          field = rootPane.getClass().getDeclaredField("contentPane");
          field.setAccessible(true);
          field.set(rootPane, null);
          rootPane = null;

          field = Window.class.getDeclaredField("windowListener");
          field.setAccessible(true);
          field.set(this, null);
        }
        catch (Exception ignored) {
        }
      }

      // http://bugs.sun.com/view_bug.do?bug_id=6614056
      try {
        final Field field = Dialog.class.getDeclaredField("modalDialogs");
        field.setAccessible(true);
        final List<?> list = (List<?>)field.get(null);
        list.remove(this);
      }
      catch (final Exception ignored) {
      }
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
        // inherit graphics so rendering hints won't be applied and trees or lists may render ugly.
        UIUtil.applyRenderingHints(g);
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
      public void windowOpened(WindowEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            myOpened = true;
            final DialogWrapper activeWrapper = getActiveWrapper();
            if (activeWrapper == null) {
              myFocusedCallback.setRejected();
              myTypeAheadDone.setRejected();
            }
          }
        });
      }

      @Override
      public void windowActivated(final WindowEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
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
            if (toFocus == null) {
              toFocus = getRootPane().getDefaultButton();
            }

            moveMousePointerOnButton(getRootPane().getDefaultButton());
            setupSelectionOnPreferredComponent(toFocus);

            if (toFocus != null) {
              final JComponent toRequest = toFocus;
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  if (isShowing() && isActive()) {
                    getFocusManager().requestFocus(toRequest, true);
                    notifyFocused(wrapper);
                  }
                }
              });
            } else {
              if (isShowing()) {
                notifyFocused(wrapper);
              }
            }
            if (myTypeAheadCallback != null) {
              myTypeAheadCallback.setDone();
            }
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

      private void moveMousePointerOnButton(final JButton button) {
        Application application = ApplicationManager.getApplication();
        if (application != null && application.hasComponent(UISettings.class)) {
          if (button != null && UISettings.getInstance().MOVE_MOUSE_ON_DEFAULT_BUTTON) {
            Point p = button.getLocationOnScreen();
            Rectangle r = button.getBounds();
            try {
              Robot robot = new Robot();
              robot.mouseMove(p.x + r.width / 2, p.y + r.height / 2);
            }
            catch (AWTException e) {
              LOG.warn(e);
            }
          }
        }
      }
    }

    private class MyComponentListener extends ComponentAdapter {
      @Override
      @SuppressWarnings({"RefusedBequest"})
      public void componentResized(ComponentEvent e) {
        if (getDialogWrapper().isAutoAdjustable()) {
          UIUtil.adjustWindowToMinimumSize(getWindow());
        }
      }
    }

    private class DialogRootPane extends JRootPane implements DataProvider {

      private final boolean myGlassPaneIsSet;

      private DialogRootPane() {
        setGlassPane(new IdeGlassPaneImpl(this));
        myGlassPaneIsSet = true;
        putClientProperty("DIALOG_ROOT_PANE", true);
      }

      @Override
      protected JLayeredPane createLayeredPane() {
        JLayeredPane p = new JBLayeredPane();
        p.setName(this.getName()+".layeredPane");
        return p;
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
      public Object getData(@NonNls String dataId) {
        final DialogWrapper wrapper = myDialogWrapper.get();
        return wrapper != null && PlatformDataKeys.UI_DISPOSABLE.is(dataId) ? wrapper.getDisposable() : null;
      }
    }


    private class MyFocusCommand extends FocusCommand implements KeyEventProcessor {

      private Context myContextOnFinish;
      private final List<KeyEvent> myEvents = new ArrayList<KeyEvent>();
      private final DialogWrapper myWrapper;

      private MyFocusCommand(DialogWrapper wrapper) {
        myWrapper = getDialogWrapper();
        setToInvalidateRequestors(false);

        Disposer.register(wrapper.getDisposable(), new Disposable() {
          @Override
          public void dispose() {
            if (!myTypeAheadDone.isProcessed()) {
              myTypeAheadDone.setDone();
            }

            flushEvents();
          }
        });
      }

      @Override
      @NotNull
      public ActionCallback run() {
        return myTypeAheadDone;
      }

      @Override
      public KeyEventProcessor getProcessor() {
        return this;
      }

      @Override
      public Boolean dispatch(@NotNull KeyEvent e, @NotNull Context context) {
        if (myWrapper == null || myTypeAheadDone.isProcessed()) return null;

        myEvents.addAll(context.getQueue());
        context.getQueue().clear();

        if (isToDispatchToDialogNow(e)) {
          return false;
        } else {
          myEvents.add(e);
          return true;
        }
      }

      private boolean isToDispatchToDialogNow(KeyEvent e) {
        return e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_TAB;
      }

      @Override
      public void finish(@NotNull Context context) {
        myContextOnFinish = context;
      }

      private void flushEvents() {
        if (myWrapper.isToDispatchTypeAhead() && myContextOnFinish != null) {
          myContextOnFinish.dispatch(myEvents);
        }
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
