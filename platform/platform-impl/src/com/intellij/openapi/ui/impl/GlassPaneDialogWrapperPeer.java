// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.impl;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.DataManager;
import com.intellij.ide.RemoteDesktopService;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.ui.popup.StackingPopupDispatcher;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneEx;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.ShadowJava2DPainter;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.jcef.HwFacadeJPanel;
import com.intellij.util.MathUtil;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public final class GlassPaneDialogWrapperPeer extends DialogWrapperPeer {
  private static final Logger LOG = Logger.getInstance(GlassPaneDialogWrapperPeer.class);

  private final DialogWrapper myWrapper;
  private MyDialog myDialog;

  public GlassPaneDialogWrapperPeer(@Nullable Project project, DialogWrapper wrapper) throws GlasspanePeerUnavailableException {
    myWrapper = wrapper;

    Window window = null;
    if (LoadingState.APP_STARTED.isOccurred()) {
      if (project == null) {
        project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
      }

      WindowManagerEx windowManager = WindowManagerEx.getInstanceEx();
      window = windowManager.suggestParentWindow(project);
      if (window == null) {
        Window focusedWindow = windowManager.getMostRecentFocusedWindow();
        if (focusedWindow instanceof IdeFrameImpl) {
          window = focusedWindow;
        }
      }
    }

    createDialog(window == null ? JOptionPane.getRootFrame() : window);
  }

  public GlassPaneDialogWrapperPeer(@NotNull DialogWrapper wrapper) throws GlasspanePeerUnavailableException {
    this(null, wrapper);
  }

  public GlassPaneDialogWrapperPeer(DialogWrapper wrapper, @NotNull Component parent) throws GlasspanePeerUnavailableException {
    myWrapper = wrapper;
    if (!parent.isShowing() && parent != JOptionPane.getRootFrame()) {
      throw new IllegalArgumentException("parent must be showing: " + parent);
    }

    Window owner = ComponentUtil.getWindow(parent);
    if (owner == null || UIUtil.isSimpleWindow(owner)) {
      owner = JOptionPane.getRootFrame();
    }

    myDialog = createDialog(owner);
  }

  @ApiStatus.Internal
  public GlassPaneDialogWrapperPeer(@NotNull DialogWrapper wrapper,
                                    @NotNull IdeGlassPaneEx glassPane) throws GlasspanePeerUnavailableException {
    myWrapper = wrapper;
    myDialog = new MyDialog(glassPane, myWrapper);
  }

  private @NotNull MyDialog createDialog(@NotNull Window owner) throws GlasspanePeerUnavailableException {
    Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (active instanceof JDialog || !(owner instanceof IdeFrame)) {
      throw new GlasspanePeerUnavailableException();
    }

    Component glassPane;
    // Not all successor of IdeFrame are frames
    if (owner instanceof JFrame) {
      glassPane = ((JFrame)owner).getGlassPane();
    }
    else if (owner instanceof JDialog) {
      glassPane = ((JDialog)owner).getGlassPane();
    }
    else {
      throw new IllegalStateException("Cannot find glass pane for " + owner.getClass().getName());
    }

    assert glassPane instanceof IdeGlassPaneEx : "GlassPane should be instance of IdeGlassPane!";
    return new MyDialog((IdeGlassPaneEx)glassPane, myWrapper);
  }

  @Override
  public void setUndecorated(final boolean undecorated) {
    LOG.assertTrue(undecorated, "Decorated dialogs are not supported!");
  }

  @Override
  public void addMouseListener(final MouseListener listener) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  @Override
  public void addMouseListener(final MouseMotionListener listener) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  @Override
  public void addKeyListener(final KeyListener listener) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  @Override
  public void toFront() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  @Override
  public void toBack() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  @Override
  public void dispose() {
    LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");

    if (myDialog != null) {
      Disposer.dispose(myDialog);
      myDialog = null;
    }
  }

  @Override
  public Container getContentPane() {
    return myDialog.getContentPane();
  }

  @Override
  public Window getOwner() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  @Override
  public Window getWindow() {
    return null;
  }

  @Override
  public JRootPane getRootPane() {
    if (myDialog == null) {
      return null;
    }

    return myDialog.getRootPane();
  }

  @Override
  public Dimension getSize() {
    return myDialog.getSize();
  }

  @Override
  public String getTitle() {
    return "";
  }

  @Override
  public Dimension getPreferredSize() {
    return myDialog.getPreferredSize();
  }

  @Override
  public void setModal(final boolean modal) {
    LOG.assertTrue(modal, "Can't be non modal!");
  }

  @Override
  public boolean isModal() {
    return true;
  }

  @Override
  public boolean isVisible() {
    return myDialog != null && myDialog.isVisible();
  }

  @Override
  public boolean isShowing() {
    return myDialog != null && myDialog.isShowing();
  }

  @Override
  public void setSize(final int width, final int height) {
    myDialog.setSize(width, height);
  }

  @Override
  public void setTitle(final String title) {
  }

  @Override
  public boolean isResizable() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  @Override
  public void setResizable(final boolean resizable) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  @Override
  public @NotNull Point getLocation() {
    return myDialog.getLocation();
  }

  @Override
  public void setLocation(final @NotNull Point p) {
    setLocation(p.x, p.y);
  }

  @Override
  public void setLocation(final int x, final int y) {
    if (myDialog == null || !myDialog.isShowing()) {
      return;
    }

    final Point _p = new Point(x, y);
    final JRootPane pane = SwingUtilities.getRootPane(myDialog);

    SwingUtilities.convertPointFromScreen(_p, pane);

    myDialog.setLocation(_p.x, _p.y);
  }

  @Override
  public CompletableFuture<?> show() {
    LOG.assertTrue(EDT.isCurrentThreadEdt(), "Access is allowed from event dispatch thread only");

    hidePopupsIfNeeded();

    myDialog.setVisible(true);

    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void setContentPane(final JComponent content) {
    myDialog.setContentPane(content);
  }

  @Override
  public void centerInParent() {
    if (myDialog != null) {
      myDialog.center();
    }
  }

  @Override
  public void validate() {
    if (myDialog != null) {
      myDialog.resetSizeCache();
      myDialog.invalidate();
    }
  }

  @Override
  public void repaint() {
    if (myDialog != null) {
      myDialog.repaint();
    }
  }

  @Override
  public void pack() {
  }

  @Override
  public void setAppIcons() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  @Override
  public boolean isHeadless() {
    return DialogWrapperPeerImpl.isHeadlessEnv();
  }

  @Override
  public void setOnDeactivationAction(@NotNull Disposable disposable, @NotNull Runnable onDialogDeactivated) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  //[kirillk] for now it only deals with the TaskWindow under Mac OS X: modal dialogs are shown behind JBPopup
  //hopefully this whole code will go away
  private void hidePopupsIfNeeded() {
    if (!SystemInfoRt.isMac) {
      return;
    }

    StackingPopupDispatcher stackingPopupDispatcher = StackingPopupDispatcher.getInstance();
    stackingPopupDispatcher.hidePersistentPopups();

    Disposer.register(myDialog, new Disposable() {
      @Override
      public void dispose() {
        stackingPopupDispatcher.restorePersistentPopups();
      }
    });
  }

  private static final class MyDialog extends HwFacadeJPanel implements Disposable, DialogWrapperDialog, UiDataProvider {
    private final WeakReference<DialogWrapper> myDialogWrapper;
    private final IdeGlassPaneEx myPane;
    private JComponent myContentPane;
    private MyRootPane myRootPane;
    private BufferedImage shadow;
    private int shadowWidth;
    private int shadowHeight;
    private final JLayeredPane myTransparentPane;
    private final Container myWrapperPane;
    private Component myPreviouslyFocusedComponent;
    private Dimension myCachedSize = null;

    private MyDialog(IdeGlassPaneEx pane, DialogWrapper wrapper) {
      setLayout(new BorderLayout());
      setOpaque(false);

      Insets insets = ShadowJava2DPainter.Type.IDE.getInsets();
      setBorder(BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));

      myPane = pane;
      myDialogWrapper = new WeakReference<>(wrapper);
      //      myProject = new WeakReference<Project>(project);

      myRootPane = new MyRootPane(this); // be careful with DialogWrapper.dispose()!
      Disposer.register(this, myRootPane);

      myContentPane = new JPanel();
      myContentPane.setOpaque(true);
      add(myContentPane, BorderLayout.CENTER);

      myTransparentPane = createTransparentPane();

      myWrapperPane = createWrapperPane();
      myWrapperPane.add(this);

      setFocusCycleRoot(true);
    }

    public void resetSizeCache() {
      myCachedSize = null;
    }

    private Container createWrapperPane() {
      final JPanel result = new JPanel() {
        @Override
        public void doLayout() {
          synchronized (getTreeLock()) {
            final Container container = getParent();
            if (container != null) {
              final Component[] components = getComponents();
              LOG.assertTrue(components.length == 1);
              for (Component c : components) {
                Point location;
                if (myCachedSize == null) {
                  myCachedSize = c.getPreferredSize();
                  location = getLocationInCenter(myCachedSize, c.getLocation());
                }
                else {
                  location = c.getLocation();
                }

                final double _width = myCachedSize.getWidth();
                final double _height = myCachedSize.getHeight();

                final DialogWrapper dialogWrapper = myDialogWrapper.get();
                if (dialogWrapper != null) {
                  final int width = (int)(_width * dialogWrapper.getHorizontalStretch());
                  final int height = (int)(_height * dialogWrapper.getVerticalStretch());
                  c.setBounds((int)location.getX(), (int)location.getY(), width, height);
                }
                else {
                  c.setBounds((int)location.getX(), (int)location.getY(), (int)_width, (int)_height);
                }
              }
            }
          }

          super.doLayout();
        }
      };

      result.setLayout(null);
      result.setOpaque(false);

      // to not pass events through transparent pane
      result.addMouseListener(new MouseAdapter() {
      });
      result.addMouseMotionListener(new MouseMotionAdapter() {
      });

      return result;
    }

    private TransparentLayeredPane getExistingTransparentPane() {
      for (int i = 0; i < myPane.getComponentCount(); i++) {
        Component c = myPane.getComponent(i);
        if (c instanceof TransparentLayeredPane) {
          return (TransparentLayeredPane)c;
        }
      }

      return null;
    }

    private boolean isTransparentPaneExist() {
      for (int i = 0; i < myPane.getComponentCount(); i++) {
        Component c = myPane.getComponent(i);
        if (c instanceof TransparentLayeredPane) {
          return true;
        }
      }

      return false;
    }

    @Override
    public void setVisible(boolean show) {
      if (show) {
        if (!isTransparentPaneExist()) {
          myPane.add(myTransparentPane);
        }
        else {
          myPreviouslyFocusedComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        }

        myTransparentPane.add(myWrapperPane);
        myTransparentPane.setLayer(myWrapperPane, myTransparentPane.getComponentCount() - 1);

        if (!myTransparentPane.isVisible()) {
          myTransparentPane.setVisible(true);
        }
      }

      super.setVisible(show);

      if (show) {
        myTransparentPane.revalidate();
        myTransparentPane.repaint();
      }
      else {
        myTransparentPane.remove(myWrapperPane);
        myTransparentPane.revalidate();
        myTransparentPane.repaint();

        if (myPreviouslyFocusedComponent != null) {
          Component component = myPreviouslyFocusedComponent;
          IdeFocusManager.getGlobalInstance()
            .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(component, true));
          myPreviouslyFocusedComponent = null;
        }

        if (myTransparentPane.getComponentCount() == 0) {
          myTransparentPane.setVisible(false);
          myPane.remove(myTransparentPane);
        }
      }
    }

    @Override
    public void paint(final Graphics g) {
      UISettings.setupAntialiasing(g);
      super.paint(g);
    }

    private JLayeredPane createTransparentPane() {
      JLayeredPane pane = getExistingTransparentPane();
      if (pane == null) {
        pane = new TransparentLayeredPane();
      }

      return pane;
    }

    @Override
    protected void paintComponent(final Graphics g) {
      final Graphics2D g2 = (Graphics2D)g;
      if (shadow != null) {
        UIUtil.drawImage(g2, shadow, 0, 0, null);
      }

      super.paintComponent(g);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
      final Container p = myTransparentPane;
      if (p != null) {
        Rectangle bounds = new Rectangle(p.getWidth() - width, p.getHeight() - height);
        JBInsets.removeFrom(bounds, getInsets());

        x = bounds.width < 0 ? bounds.width / 2 : MathUtil.clamp(x, bounds.x, bounds.x + bounds.width);
        y = bounds.height < 0 ? bounds.height / 2 : MathUtil.clamp(y, bounds.y, bounds.y + bounds.height);
      }
      super.setBounds(x, y, width, height);

      if (RemoteDesktopService.isRemoteSession()) {
        shadow = null;
      }
      else if (shadow == null || shadowWidth != width || shadowHeight != height) {
        shadow = ShadowBorderPainter.createShadow(this, width, height);
        shadowWidth = width;
        shadowHeight = height;
      }
    }

    @Override
    public void dispose() {
      remove(getContentPane());
      setVisible(false);
      myRootPane = null;
    }

    public void setContentPane(JComponent content) {
      if (myContentPane != null) {
        remove(myContentPane);
      }

      myContentPane = content;
      myContentPane.setOpaque(true); // should be opaque
      add(myContentPane, BorderLayout.CENTER);
    }

    public JComponent getContentPane() {
      return myContentPane;
    }

    @Override
    public JRootPane getRootPane() {
      return myRootPane;
    }

    @Override
    public DialogWrapper getDialogWrapper() {
      return myDialogWrapper.get();
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      DialogWrapper wrapper = myDialogWrapper.get();
      DataSink.uiDataSnapshot(sink, wrapper);
    }

    @Override
    public void setSize(int width, int height) {
      Point location = getLocation();
      Rectangle rect = new Rectangle(location.x, location.y, width, height);
      ScreenUtil.fitToScreen(rect);
      if (location.x != rect.x || location.y != rect.y) {
        setLocation(rect.x, rect.y);
      }

      super.setSize(rect.width, rect.height);
    }

    @Contract("_,!null->!null")
    private Point getLocationInCenter(@NotNull Dimension size, @Nullable Point _default) {
      if (myTransparentPane != null) {
        final Dimension d = myTransparentPane.getSize();
        return new Point((d.width - size.width) / 2, (d.height - size.height) / 2);
      }

      return _default;
    }

    public void center() {
      final Point location = getLocationInCenter(getSize(), null);
      if (location != null) {
        setLocation(location);
        repaint();
      }
    }

    public void setDefaultButton(JButton defaultButton) {
      //((JComponent)myPane).getRootPane().setDefaultButton(defaultButton);
    }
  }

  private static final class MyRootPane extends JRootPane implements Disposable {
    private MyDialog myDialog;

    private MyRootPane(final MyDialog dialog) {
      myDialog = dialog;
    }

    @Override
    protected JLayeredPane createLayeredPane() {
      JLayeredPane p = new JBLayeredPane();
      p.setName(this.getName() + ".layeredPane");
      return p;
    }

    @Override
    public void dispose() {
      DialogWrapper.cleanupRootPane(this);
      myDialog = null;
    }

    @Override
    public void registerKeyboardAction(final ActionListener anAction,
                                       final String aCommand,
                                       final KeyStroke aKeyStroke,
                                       final int aCondition) {
      myDialog.registerKeyboardAction(anAction, aCommand, aKeyStroke, aCondition);
    }

    @Override
    public void unregisterKeyboardAction(final KeyStroke aKeyStroke) {
      myDialog.unregisterKeyboardAction(aKeyStroke);
    }

    @Override
    public void setDefaultButton(final JButton defaultButton) {
      myDialog.setDefaultButton(defaultButton);
    }

    @Override
    public void setContentPane(Container contentPane) {
      super.setContentPane(contentPane);
      contentPane.addMouseMotionListener(new MouseMotionAdapter() {
      }); // listen to mouse motion events for a11y
    }
  }

  public static final class GlasspanePeerUnavailableException extends Exception {
  }

  public static final class TransparentLayeredPane extends JBLayeredPane {
    private TransparentLayeredPane() {
      setLayout(new BorderLayout());
      setOpaque(false);

      // to not pass events through transparent pane
      addMouseListener(new MouseAdapter() {
      });
      addMouseMotionListener(new MouseMotionAdapter() {
      });
    }

    @Override
    public void addNotify() {
      final Container container = getParent();
      if (container != null) {
        setBounds(0, 0, container.getWidth(), container.getHeight());
      }

      super.addNotify();
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
      return getComponentCount() <= 1;
    }
  }
}
