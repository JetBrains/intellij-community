/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.CommandProcessorEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.popup.StackingPopupDispatcher;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.FocusCommand;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.KeyEventProcessor;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.popup.StackingPopupDispatcherImpl;
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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DialogWrapperPeerImpl extends DialogWrapperPeer implements FocusTrackbackProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.DialogWrapper");

  private DialogWrapper myWrapper;
  private AbstractDialog myDialog;
  private boolean myCanBeParent = true;
  /*
   * Default dialog's actions.
   */
  private WindowManagerEx myWindowManager;
  private final java.util.List<Runnable> myDisposeActions = new ArrayList<Runnable>();
  private Project myProject;

  private final ActionCallback myWindowFocusedCallback = new ActionCallback("DialogFocusedCallback");
  private final ActionCallback myTypeAheadDone = new ActionCallback("DialogTypeAheadDone");

  /**
   * Creates modal <code>DialogWrapper</code>. The currently active window will be the dialog's parent.
   *
   * @param project     parent window for the dialog will be calculated based on focused window for the
   *                    specified <code>project</code>. This parameter can be <code>null</code>. In this case parent window
   *                    will be suggested based on current focused window.
   * @param canBeParent specifies whether the dialog can be parent for other windows. This parameter is used
   *                    by <code>WindowManager</code>.
   */
  protected DialogWrapperPeerImpl(DialogWrapper wrapper, Project project, boolean canBeParent) {
    myWrapper = wrapper;
    myWindowManager = null;
    Application application = ApplicationManager.getApplication();
    if (application != null && application.hasComponent(WindowManager.class)) {
      myWindowManager = (WindowManagerEx)WindowManager.getInstance();
    }

    Window window = null;
    if (myWindowManager != null) {

      if (project == null) {
        project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
      }

      myProject = project;

      window = myWindowManager.suggestParentWindow(project);
      if (window == null) {
        Window focusedWindow = myWindowManager.getMostRecentFocusedWindow();
        if (focusedWindow instanceof IdeFrameImpl) {
          window = focusedWindow;
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

    createDialog(owner, canBeParent);
  }

  protected DialogWrapperPeerImpl(DialogWrapper wrapper, boolean canBeParent) {
    this(wrapper, (Project)null, canBeParent);
  }

  public boolean isHeadless() {
    Application app = ApplicationManager.getApplication();
    if (app == null) return GraphicsEnvironment.isHeadless();

    return app.isUnitTestMode() || app.isHeadlessEnvironment();
  }

  /**
   * @param parent parent component whicg is used to canculate heavy weight window ancestor.
   *               <code>parent</code> cannot be <code>null</code> and must be showing.
   */
  protected DialogWrapperPeerImpl(DialogWrapper wrapper, @NotNull Component parent, boolean canBeParent) {
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

  public DialogWrapperPeerImpl(final DialogWrapper wrapper, final boolean canBeParent, final boolean tryToolkitModal) {
    myWrapper = wrapper;
    myWindowManager = null;
    Application application = ApplicationManager.getApplication();
    if (application != null && application.hasComponent(WindowManager.class)) {
      myWindowManager = (WindowManagerEx)WindowManager.getInstance();
    }
    if (UIUtil.hasJdk6Dialogs()) {
      createDialog(null, canBeParent);
      if (tryToolkitModal && !isHeadless()) {
        UIUtil.setToolkitModal((MyDialog)myDialog);
      }
    }
    else {
      createDialog(JOptionPane.getRootFrame(), canBeParent);
    }
  }

  public void setUndecorated(boolean undecorated) {
    myDialog.setUndecorated(undecorated);
  }

  public void addMouseListener(MouseListener listener) {
    myDialog.addMouseListener(listener);
  }

  public void addMouseListener(MouseMotionListener listener) {
    myDialog.addMouseMotionListener(listener);
  }

  public void addKeyListener(KeyListener listener) {
    myDialog.addKeyListener(listener);
  }

  private void createDialog(Window owner, boolean canBeParent) {
    if (isHeadless()) {
      myDialog = new HeadlessDialog();
      return;
    }

    if (owner instanceof Frame) {
      myDialog = new MyDialog((Frame)owner, myWrapper, myProject, myWindowFocusedCallback, myTypeAheadDone);
    }
    else {
      myDialog = new MyDialog((Dialog)owner, myWrapper, myProject, myWindowFocusedCallback, myTypeAheadDone);
    }
    myDialog.setModal(true);
    myCanBeParent = canBeParent;

  }


  public void toFront() {
    myDialog.toFront();
  }

  public void toBack() {
    myDialog.toBack();
  }

  protected void dispose() {
    LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");
    for (Runnable runnable : myDisposeActions) {
      runnable.run();
    }
    myDisposeActions.clear();
    final JRootPane root = myDialog.getRootPane();

    Runnable disposer = new Runnable() {
      public void run() {
        myDialog.dispose();
        myProject = null;
        /*
        if (myWindowManager == null) {
          myDialog.dispose();
        }
        else {
          myWindowManager.hideDialog(myDialog, myProject);
        }
        */

        SwingUtilities.invokeLater(new Runnable() {
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

  @Nullable
  public Container getContentPane() {
    return getRootPane() != null ? myDialog.getContentPane() : null;
  }

  /**
   * @see javax.swing.JDialog#validate
   */
  public void validate() {
    myDialog.validate();
  }

  /**
   * @see javax.swing.JDialog#repaint
   */
  public void repaint() {
    myDialog.repaint();
  }

  public Window getOwner() {
    return myDialog.getOwner();
  }

  public Window getWindow() {
    return myDialog.getWindow();
  }

  public JRootPane getRootPane() {
    return myDialog.getRootPane();
  }

  public Dimension getSize() {
    return myDialog.getSize();
  }

  public String getTitle() {
    return myDialog.getTitle();
  }

  /**
   * @see java.awt.Window#pack
   */
  public void pack() {
    myDialog.pack();
  }

  public void setIconImages(final List<Image> image) {
    UIUtil.updateDialogIcon(myDialog.getWindow(), image);
  }

  public void setAppIcons() {
    setIconImages(AppUIUtil.getAppIconImages());
  }

  public Dimension getPreferredSize() {
    return myDialog.getPreferredSize();
  }

  public void setModal(boolean modal) {
    myDialog.setModal(modal);
  }

  public boolean isVisible() {
    return myDialog.isVisible();
  }

  public boolean isShowing() {
    return myDialog.isShowing();
  }

  public void setSize(int width, int height) {
    myDialog.setSize(width, height);
  }

  public void setTitle(String title) {
    myDialog.setTitle(title);
  }

  public void isResizable() {
    myDialog.isResizable();
  }

  public void setResizable(boolean resizable) {
    myDialog.setResizable(resizable);
  }

  public Point getLocation() {
    return myDialog.getLocation();
  }

  public void setLocation(Point p) {
    myDialog.setLocation(p);
  }

  public void setLocation(int x, int y) {
    myDialog.setLocation(x, y);
  }

  public ActionCallback show() {
    final ActionCallback result = new ActionCallback();

    LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");

    final AnCancelAction anCancelAction = new AnCancelAction();
    final JRootPane rootPane = getRootPane();
    anCancelAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)), rootPane);
    myDisposeActions.add(new Runnable() {
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
      /*
      if (ApplicationManager.getApplication() != null) {
        if (ApplicationManager.getApplication().getCurrentWriteAction(null) != null) {
          LOG.warn(
            "Showing of a modal dialog inside write-action may be dangerous and resulting in unpredictable behavior! Current modalityState=" + ModalityState.current(), new Exception());
        }
      }
      */
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

      myDialog.getFocusManager().doWhenFocusSettlesDown(new Runnable() {
        public void run() {
          result.setDone();
        }
      });
    }

    return result;
  }

//[kirillk] for now it only deals with the TaskWindow under Mac OS X: modal dialogs are shown behind JBPopup

  //hopefully this whole code will go away
  private void hidePopupsIfNeeded() {
    if (!SystemInfo.isMac) return;

    StackingPopupDispatcherImpl.getInstance().hidePersistentPopups();
    myDisposeActions.add(new Runnable() {
      public void run() {
        StackingPopupDispatcherImpl.getInstance().restorePersistentPopups();
      }
    });
  }

  public FocusTrackback getFocusTrackback() {
    return myDialog.getFocusTrackback();
  }

  private class AnCancelAction extends AnAction implements DumbAware {
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

    public void actionPerformed(AnActionEvent e) {
      myWrapper.doCancelAction(e.getInputEvent());
    }
  }


  private static class MyDialog extends JDialog implements DialogWrapperDialog, DataProvider, FocusTrackback.Provider, Queryable, AbstractDialog {
    private final WeakReference<DialogWrapper> myDialogWrapper;
    /**
     * Initial size of the dialog. When the dialog is being closed and
     * current size of the dialog is not equals to the initial sizethen the
     * current (changed) size is stored in the <code>DimensionService</code>.
     */
    private Dimension myInitialSize;
    private String myDimensionServiceKey;
    private boolean myOpened = false;

    private FocusTrackback myFocusTrackback;
    private MyDialog.MyWindowListener myWindowListener;
    private MyDialog.MyComponentListener myComponentListener;

    private final WeakReference<Project> myProject;
    private ActionCallback myFocusedCallback;
    private ActionCallback myTypeAheadDone;

    public MyDialog(Dialog owner, DialogWrapper dialogWrapper, Project project, ActionCallback focused, ActionCallback typeAheadDone) {
      super(owner);
      myDialogWrapper = new WeakReference<DialogWrapper>(dialogWrapper);
      myProject = project != null ? new WeakReference<Project>(project) : null;
      initDialog(focused, typeAheadDone);
    }

    public MyDialog(Frame owner, DialogWrapper dialogWrapper, Project project, ActionCallback focused, ActionCallback typeAheadDone) {
      super(owner);
      myDialogWrapper = new WeakReference<DialogWrapper>(dialogWrapper);
      myProject = project != null ? new WeakReference<Project>(project) : null;
      initDialog(focused, typeAheadDone);
    }

    private void initDialog(ActionCallback focused, ActionCallback typeAheadDone) {
      myFocusedCallback = focused;
      myTypeAheadDone = typeAheadDone;

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

    public JDialog getWindow() {
      return this;
    }

    public void putInfo(Map<String, String> info) {
      info.put("dialog", getTitle());
    }

    public FocusTrackback getFocusTrackback() {
      return myFocusTrackback;
    }

    public DialogWrapper getDialogWrapper() {
      return myDialogWrapper.get();
    }

    public void centerInParent() {
      setLocationRelativeTo(getOwner());
    }

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

    public void setSize(int width, int height) {
      Point location = getLocation();
      Rectangle rect = new Rectangle(location.x, location.y, width, height);
      ScreenUtil.fitToScreen(rect);
      if (location.x != rect.x || location.y != rect.y) {
        setLocation(rect.x, rect.y);
      }

      super.setSize(rect.width, rect.height);
    }

    public void setBounds(int x, int y, int width, int height) {
      Rectangle rect = new Rectangle(x, y, width, height);
      ScreenUtil.fitToScreen(rect);
      super.setBounds(rect.x, rect.y, rect.width, rect.height);
    }

    public void setBounds(Rectangle r) {
      ScreenUtil.fitToScreen(r);
      super.setBounds(r);
    }

    protected JRootPane createRootPane() {
      return new DialogRootPane();
    }

    public void show() {
      myFocusTrackback = new FocusTrackback(myDialogWrapper, this, true);

      final DialogWrapper dialogWrapper = getDialogWrapper();

      pack();
      setSize((int)(getWidth() * dialogWrapper.getHorizontalStretch()), (int)(getHeight() * dialogWrapper.getVerticalStretch()));

      // Restore dialog's size and location

      myDimensionServiceKey = dialogWrapper.getDimensionKey();
      Point location = null;

      if (myDimensionServiceKey != null) {
        final Project projectGuess = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this));
        location = DimensionService.getInstance().getLocation(myDimensionServiceKey, projectGuess);
        Dimension size = DimensionService.getInstance().getSize(myDimensionServiceKey, projectGuess);
        if (size != null) {
          myInitialSize = (Dimension)size.clone();
          setSize(myInitialSize);
        }
      }

      if (myInitialSize == null) {
        myInitialSize = getSize();
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

      final Rectangle bounds = getBounds();
      ScreenUtil.fitToScreen(bounds);
      setBounds(bounds);

      addWindowListener(new WindowAdapter() {
        public void windowActivated(final WindowEvent e) {
          final DialogWrapper wrapper = getDialogWrapper();
          if (wrapper != null && myFocusTrackback != null) {
            myFocusTrackback.registerFocusComponent(new FocusTrackback.ComponentQuery() {
              public Component getComponent() {
                return wrapper.getPreferredFocusedComponent();
              }
            });
          }
        }

        public void windowDeactivated(final WindowEvent e) {
          if (!isModal()) {
            final Ref<IdeFocusManager> focusManager = new Ref<IdeFocusManager>(null);
            if (myProject != null && myProject.get() != null && !myProject.get().isDisposed()) {
              focusManager.set(getFocusManager());
              focusManager.get().doWhenFocusSettlesDown(new Runnable() {
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
      });

      if (Registry.is("actionSystem.fixLostTyping")) {
        final IdeEventQueue queue = IdeEventQueue.getInstance();
        if (queue != null) {
          queue.getKeyEventDispatcher().resetState();
        }

        if (myProject != null) {
          Project project = myProject.get();
          if (project != null && !project.isDisposed() && project.isInitialized()) {
            IdeFocusManager.findInstanceByComponent(this).requestFocus(new MyFocusCommand(dialogWrapper), true);
          }
        }
      }

      super.show();
    }

    public IdeFocusManager getFocusManager() {
      if (myProject != null && myProject.get() != null && !myProject.get().isDisposed()) {
        return IdeFocusManager.getInstance(myProject.get());
      } else {
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

    @Deprecated
    public void hide() {
      super.hide();
      if (myFocusTrackback != null && !(myFocusTrackback.isSheduledForRestore() || myFocusTrackback.isWillBeSheduledForRestore())) {
        myFocusTrackback.setWillBeSheduledForRestore();
        IdeFocusManager mgr = getFocusManager();
        Runnable r = new Runnable() {
          public void run() {
            myFocusTrackback.restoreFocus();
            myFocusTrackback = null;
          }
        };
        mgr.doWhenFocusSettlesDown(r);
      }
    }

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
        try {
          Method method = strategy.getClass().getMethod("dispose");   // added in JDK 1.6 so cannot call directly
          method.invoke(strategy);
        }
        catch (Exception ex) {
          // ignore
        }
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
        catch (Exception e) {
        }
      }

      // http://bugs.sun.com/view_bug.do?bug_id=6614056
      try {
        final Field field = Dialog.class.getDeclaredField("modalDialogs");
        field.setAccessible(true);
        final List<?> list = (List<?>)field.get(null);
        list.remove(this);
      }
      catch (final Exception ex) {
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
      UIUtil.applyRenderingHints(g);
      super.paint(g);
    }

    private class MyWindowListener extends WindowAdapter {
      public void windowClosing(WindowEvent e) {
        DialogWrapper dialogWrapper = getDialogWrapper();
        if (dialogWrapper.shouldCloseOnCross()) {
          dialogWrapper.doCancelAction(e);
        }
      }

      public void windowClosed(WindowEvent e) {
        saveSize();
      }

      public void saveSize() {
        if (myDimensionServiceKey != null &&
            myInitialSize != null &&
            myOpened) { // myInitialSize can be null only if dialog is disposed before first showing
          final Project projectGuess = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(MyDialog.this));

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

      public void windowOpened(final WindowEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            myOpened = true;
            final DialogWrapper activeWrapper = getActiveWrapper();
            if (activeWrapper == null) {
              myFocusedCallback.setRejected();
              myTypeAheadDone.setRejected();
              return;
            }

            JComponent toFocus = activeWrapper.getPreferredFocusedComponent();
            if (toFocus == null) {
              toFocus = getRootPane().getDefaultButton();
            }

            moveMousePointerOnButton(getRootPane().getDefaultButton());
            setupSelectionOnPreferredComponent(toFocus);

            if (toFocus != null) {
              final JComponent toRequest = toFocus;
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  IdeFocusManager.findInstanceByComponent(e.getWindow()).requestFocus(toRequest, true);
                  notifyFocused(activeWrapper);
                }
              });
            } else {
              notifyFocused(activeWrapper);
            }
          }
        });
      }

      private void notifyFocused(DialogWrapper wrapper) {
        myFocusedCallback.setDone();
        final long timeout = wrapper.getTypeAheadTimeoutMs();
        if (timeout > 0) {
          SimpleTimer.getInstance().setUp(new EdtRunnable() {
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
            catch (AWTException exc) {
              exc.printStackTrace();
            }
          }
        }
      }
    }

    private class MyComponentListener extends ComponentAdapter {
      @SuppressWarnings({"RefusedBequest"})
      public void componentResized(ComponentEvent e) {
        final JRootPane pane = getRootPane();
        if (pane == null) return;
        final Dimension minSize = pane.getMinimumSize();
        final Dimension size = pane.getSize();
        final Dimension winSize = getSize();
        if (minSize.width > size.width) {
          winSize.width += minSize.width - size.width;
        }
        if (minSize.height > size.height) {
          winSize.height += minSize.height - size.height;
        }

        if (!winSize.equals(getSize())) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (isShowing()) {
                setSize(winSize);
              }
            }
          });
        }
      }
    }

    private class DialogRootPane extends JRootPane implements DataProvider {

      private final boolean myGlassPaneIsSet;

      private DialogRootPane() {
        setGlassPane(new IdeGlassPaneImpl(this));
        myGlassPaneIsSet = true;
      }

      @Override
      public void setGlassPane(final Component glass) {
        if (myGlassPaneIsSet) {
          LOG.warn("Setting of glass pane for DialogWrapper is prohibited", new Exception());
          return;
        }

        super.setGlassPane(glass);
      }

      public Object getData(@NonNls String dataId) {
        final DialogWrapper wrapper = myDialogWrapper.get();
        return PlatformDataKeys.UI_DISPOSABLE.is(dataId) ? wrapper.getDisposable() : null;
      }
    }


    private class MyFocusCommand extends FocusCommand implements KeyEventProcessor {

      private Context myContextOnFinish;
      private final ArrayList<KeyEvent> myEvents = new ArrayList<KeyEvent>();
      private final DialogWrapper myWrapper;

      private MyFocusCommand(DialogWrapper wrapper) {
        myWrapper = getDialogWrapper();

        Disposer.register(wrapper.getDisposable(), new Disposable() {
          public void dispose() {
            if (!myTypeAheadDone.isProcessed()) {
              myTypeAheadDone.setDone();
            }

            flushEvents();
          }
        });
      }

      public ActionCallback run() {
        return myTypeAheadDone;
      }

      @Override
      public KeyEventProcessor getProcessor() {
        return this;
      }

      public Boolean dispatch(KeyEvent e, Context context) {
        if (myWrapper == null || myTypeAheadDone.isProcessed()) return null;

        myEvents.addAll(context.getQueue());
        context.getQueue().clear();

        if (isToDipatchToDialogNow(e)) {
          return false;
        } else {
          myEvents.add(e);
          return true;
        }
      }

      private boolean isToDipatchToDialogNow(KeyEvent e) {
        return e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_TAB;        
      }

      public void finish(Context context) {
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

  public void setContentPane(JComponent content) {
    myDialog.setContentPane(content);
  }

  public void centerInParent() {
    myDialog.centerInParent();
  }

  interface AbstractDialog extends Disposable {
    void setUndecorated(boolean undecorated);

    void addMouseListener(MouseListener listener);

    void addMouseMotionListener(MouseMotionListener listener);

    void addKeyListener(KeyListener listener);

    void setModal(boolean b);

    void toFront();

    void setContentPane(Container content);

    void centerInParent();

    void toBack();

    JRootPane getRootPane();

    void remove(Component root);

    Container getContentPane();

    void validate();

    void repaint();

    Window getOwner();

    JDialog getWindow();

    Dimension getSize();

    String getTitle();

    void pack();

    Dimension getPreferredSize();

    boolean isVisible();

    boolean isShowing();

    void setSize(int width, int height);

    void setTitle(String title);

    boolean isResizable();

    void setResizable(boolean resizable);

    Point getLocation();

    void setLocation(Point p);

    void setLocation(int x, int y);

    boolean isModal();

    void show();

    IdeFocusManager getFocusManager();

    FocusTrackback getFocusTrackback();
  }

  class HeadlessDialog implements AbstractDialog {
    public void setUndecorated(boolean undecorated) {
    }

    public void addMouseListener(MouseListener listener) {
    }

    public void addMouseMotionListener(MouseMotionListener listener) {
    }

    public void addKeyListener(KeyListener listener) {
    }

    public void setModal(boolean b) {
    }

    public void toFront() {
    }

    public void setContentPane(Container content) {
    }

    public void centerInParent() {
    }

    public void toBack() {
    }

    public JRootPane getRootPane() {
      return null;
    }

    public void remove(Component root) {
    }

    public Container getContentPane() {
      return null;
    }

    public void validate() {
    }

    public void repaint() {
    }

    public Window getOwner() {
      return null;
    }

    public JDialog getWindow() {
      return null;
    }

    public Dimension getSize() {
      return null;
    }

    public String getTitle() {
      return null;
    }

    public void pack() {
    }

    public Dimension getPreferredSize() {
      return null;
    }

    public boolean isVisible() {
      return false;
    }

    public boolean isShowing() {
      return false;
    }

    public void setSize(int width, int height) {
    }

    public void setTitle(String title) {
    }

    public boolean isResizable() {
      return false;
    }

    public void setResizable(boolean resizable) {
    }

    public Point getLocation() {
      return null;
    }

    public void setLocation(Point p) {
    }

    public void setLocation(int x, int y) {
    }

    public boolean isModal() {
      return false;
    }

    public void show() {
    }

    public IdeFocusManager getFocusManager() {
      return null;
    }

    public FocusTrackback getFocusTrackback() {
      return null;
    }

    public void dispose() {
    }
  }
}
