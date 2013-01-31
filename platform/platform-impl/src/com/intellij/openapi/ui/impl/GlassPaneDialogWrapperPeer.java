/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneEx;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.popup.StackingPopupDispatcherImpl;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * @author spleaner
 */
public class GlassPaneDialogWrapperPeer extends DialogWrapperPeer implements FocusTrackbackProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer");

  private DialogWrapper myWrapper;
  private WindowManagerEx myWindowManager;
  private Project myProject;
  private MyDialog myDialog;
  private boolean myCanBeParent;

  public GlassPaneDialogWrapperPeer(DialogWrapper wrapper, Project project, boolean canBeParent) throws GlasspanePeerUnavailableException {
    myWrapper = wrapper;
    myCanBeParent = canBeParent;

    myWindowManager = null;
    Application application = ApplicationManager.getApplication();
    if (application != null && application.hasComponent(WindowManager.class)) {
      myWindowManager = (WindowManagerEx) WindowManager.getInstance();
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
    } else {
      owner = JOptionPane.getRootFrame();
    }

    createDialog(owner);
  }

  public GlassPaneDialogWrapperPeer(DialogWrapper wrapper, boolean canBeParent) throws GlasspanePeerUnavailableException {
    this(wrapper, (Project) null, canBeParent);
  }

  public GlassPaneDialogWrapperPeer(DialogWrapper wrapper, @NotNull Component parent, boolean canBeParent)
      throws GlasspanePeerUnavailableException {
    myWrapper = wrapper;
    myCanBeParent = canBeParent;
    if (!parent.isShowing() && parent != JOptionPane.getRootFrame()) {
      throw new IllegalArgumentException("parent must be showing: " + parent);
    }
    myWindowManager = null;
    Application application = ApplicationManager.getApplication();
    if (application != null && application.hasComponent(WindowManager.class)) {
      myWindowManager = (WindowManagerEx) WindowManager.getInstance();
    }

    Window owner = parent instanceof Window ? (Window) parent : (Window) SwingUtilities.getAncestorOfClass(Window.class, parent);
    if (!(owner instanceof Dialog) && !(owner instanceof Frame)) {
      owner = JOptionPane.getRootFrame();
    }

    createDialog(owner);
  }

  private void createDialog(final Window owner) throws GlasspanePeerUnavailableException {
    Window active = DefaultKeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (!(active instanceof JDialog) && owner instanceof IdeFrame) {
      final JFrame frame = (JFrame) owner;
      final JComponent glassPane = (JComponent) frame.getGlassPane();

      assert glassPane instanceof IdeGlassPaneEx : "GlassPane should be instance of IdeGlassPane!";
      myDialog = new MyDialog((IdeGlassPaneEx) glassPane, myWrapper, myProject);
    } else {
      throw new GlasspanePeerUnavailableException();
    }
  }

  public FocusTrackback getFocusTrackback() {
    if (myDialog != null) {
      return myDialog.getFocusTrackback();
    }
    
    return null;
  }

  public void setUndecorated(final boolean undecorated) {
    LOG.assertTrue(undecorated, "Decorated dialogs are not supported!");
  }

  public void addMouseListener(final MouseListener listener) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public void addMouseListener(final MouseMotionListener listener) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public void addKeyListener(final KeyListener listener) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public void toFront() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public void toBack() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public void dispose() {
    LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");

    if (myDialog != null) {
      Disposer.dispose(myDialog);
      myDialog = null;
      myProject = null;
      myWindowManager = null;
    }
  }

  public Container getContentPane() {
    return myDialog.getContentPane();
  }

  public Window getOwner() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public Window getWindow() {
    return null;
  }

  public JRootPane getRootPane() {
    if (myDialog == null) {
      return null;
    }

    return myDialog.getRootPane();
  }

  public Dimension getSize() {
    return myDialog.getSize();
  }

  public String getTitle() {
    return "";
  }

  public Dimension getPreferredSize() {
    return myDialog.getPreferredSize();
  }

  public void setModal(final boolean modal) {
    LOG.assertTrue(modal, "Can't be non modal!");
  }

  @Override
  public boolean isModal() {
    return true;
  }

  public boolean isVisible() {
    return myDialog != null && myDialog.isVisible();
  }

  public boolean isShowing() {
    return myDialog != null && myDialog.isShowing();
  }

  public void setSize(final int width, final int height) {
    myDialog.setSize(width, height);
  }

  public void setTitle(final String title) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  // TODO: WTF?! VOID?!!!
  public void isResizable() {
  }

  public void setResizable(final boolean resizable) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public Point getLocation() {
    return myDialog.getLocation();
  }

  public void setLocation(final Point p) {
    setLocation(p.x, p.y);
  }

  public void setLocation(final int x, final int y) {
    if (myDialog == null || !myDialog.isShowing()) {
      return;
    }

    final Point _p = new Point(x, y);
    final JRootPane pane = SwingUtilities.getRootPane(myDialog);

    SwingUtilities.convertPointFromScreen(_p, pane);

    final Insets insets = myDialog.getInsets();

    // todo: fix coords to include shadow (border) paddings
    // todo: reimplement dragging in every client to calculate window position properly
    int _x = _p.x - insets.left;
    int _y = _p.y - insets.top;

    final Container container = myDialog.getTransparentPane();

    _x = _x > 0 ? (_x + myDialog.getWidth() < container.getWidth() ? _x : container.getWidth() - myDialog.getWidth()) : 0;
    _y = _y > 0 ? (_y + myDialog.getHeight() < container.getHeight() ? _y : container.getHeight() - myDialog.getHeight()) : 0;

    myDialog.setLocation(_x, _y);
  }

  public ActionCallback show() {
    LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");

    hidePopupsIfNeeded();

    myDialog.setVisible(true);

    return new ActionCallback.Done();
  }

  public void setContentPane(final JComponent content) {
    myDialog.setContentPane(content);
  }

  public void centerInParent() {
    if (myDialog != null) {
      myDialog.center();
    }
  }

  public void validate() {
    if (myDialog != null) {
      myDialog.resetSizeCache();
      myDialog.invalidate();
    }
  }

  public void repaint() {
    if (myDialog != null) {
      myDialog.repaint();
    }
  }

  public void pack() {
  }

  public void setIconImages(final List<Image> image) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public void setAppIcons() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  @Override
  public boolean isHeadless() {
    return DialogWrapperPeerImpl.isHeadlessEnv();
  }

  //[kirillk] for now it only deals with the TaskWindow under Mac OS X: modal dialogs are shown behind JBPopup
  //hopefully this whole code will go away
  private void hidePopupsIfNeeded() {
    if (!SystemInfo.isMac) return;

    StackingPopupDispatcherImpl.getInstance().hidePersistentPopups();

    Disposer.register(myDialog, new Disposable() {
      public void dispose() {
        StackingPopupDispatcherImpl.getInstance().restorePersistentPopups();
      }
    });
  }

  private static class MyDialog extends JPanel implements Disposable, DialogWrapperDialog, DataProvider, FocusTrackback.Provider {
    private final WeakReference<DialogWrapper> myDialogWrapper;
    private final IdeGlassPaneEx myPane;
    private JComponent myContentPane;
    private MyRootPane myRootPane;
    private BufferedImage shadow;
    private final JLayeredPane myTransparentPane;
    private JButton myDefaultButton;
    private Dimension myShadowSize = null;
    private final Container myWrapperPane;
    private Component myPreviouslyFocusedComponent;
    private Dimension myCachedSize = null;

    private MyDialog(IdeGlassPaneEx pane, DialogWrapper wrapper, Project project) {
      setLayout(new BorderLayout());
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder(ShadowBorderPainter.TOP_SIZE, ShadowBorderPainter.SIDE_SIZE,
          ShadowBorderPainter.BOTTOM_SIZE, ShadowBorderPainter.SIDE_SIZE));

      myPane = pane;
      myDialogWrapper = new WeakReference<DialogWrapper>(wrapper);
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
                } else {
                  location = c.getLocation();
                }

                final double _width = myCachedSize.getWidth();
                final double _height = myCachedSize.getHeight();

                final DialogWrapper dialogWrapper = myDialogWrapper.get();
                if (dialogWrapper != null) {
                  final int width = (int) (_width * dialogWrapper.getHorizontalStretch());
                  final int height = (int) (_height * dialogWrapper.getVerticalStretch());
                  c.setBounds((int) location.getX(), (int) location.getY(), width, height);
                } else {
                  c.setBounds((int) location.getX(), (int) location.getY(), (int) _width, (int) _height);
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

    public Container getTransparentPane() {
      return myTransparentPane;
    }

    private TransparentLayeredPane getExistingTransparentPane() {
      for (int i = 0; i < myPane.getComponentCount(); i++) {
        Component c = myPane.getComponent(i);
        if (c instanceof TransparentLayeredPane) {
          return (TransparentLayeredPane) c;
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
    public void setVisible(final boolean show) {
      if (show) {
        if (!isTransparentPaneExist()) {
          myPane.add(myTransparentPane);
        } else {
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
      } else {
        myTransparentPane.remove(myWrapperPane);
        myTransparentPane.revalidate();
        myTransparentPane.repaint();

        if (myPreviouslyFocusedComponent != null) {
          myPreviouslyFocusedComponent.requestFocus();
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
      UIUtil.applyRenderingHints(g);
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
      final Graphics2D g2 = (Graphics2D) g;
      if (shadow != null) {
        g2.drawImage(shadow, 0, 0, null);
      }

      super.paintComponent(g);
    }

    @Override
    public void setBounds(final int x, final int y, final int width, final int height) {
      super.setBounds(x, y, width, height);

      if (myShadowSize == null || !myShadowSize.equals(getSize())) {
        createShadow();
        myShadowSize = getSize();
      }
    }

    @Override
    public void setLocation(final int x, final int y) {
      final Container p = myTransparentPane;
      if (p != null) {
        final Dimension s = p.getSize();

        final int _x = (int) (x + getWidth() > s.getWidth() ? s.getWidth() - getWidth() : x);
        final int _y = (int) (y + getHeight() > s.getHeight() ? s.getHeight() - getHeight() : y);
        super.setLocation(_x, _y);
      } else {
        super.setLocation(x, y);
      }
    }

    private void createShadow() {
      if (!UISettings.isRemoteDesktopConnected()) {
        shadow = ShadowBorderPainter.createShadow(this, getWidth(), getHeight());
      }
    }

    public void dispose() {
      remove(getContentPane());
      repaint();

      final Runnable disposer = new Runnable() {
        public void run() {
          setVisible(false);
        }
      };

      if (EventQueue.isDispatchThread()) {
        disposer.run();
      } else {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(disposer);
      }

      myRootPane = null;
    }

    public void setContentPane(JComponent content) {
      if (myContentPane != null) {
        remove(myContentPane);
        myContentPane = null;
      }

      myContentPane = content;
      myContentPane.setOpaque(true); // should be opaque
      add(myContentPane, BorderLayout.CENTER);
    }

    public JComponent getContentPane() {
      return myContentPane;
    }

    public JRootPane getRootPane() {
      return myRootPane;
    }

    public DialogWrapper getDialogWrapper() {
      return myDialogWrapper.get();
    }

    public Object getData(@NonNls final String dataId) {
      final DialogWrapper wrapper = myDialogWrapper.get();
      if (wrapper instanceof DataProvider) {
        return ((DataProvider) wrapper).getData(dataId);
      } else if (wrapper instanceof TypeSafeDataProvider) {
        TypeSafeDataProviderAdapter adapter = new TypeSafeDataProviderAdapter((TypeSafeDataProvider) wrapper);
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

    public FocusTrackback getFocusTrackback() {
      return null;
    }

    @Nullable
    private Point getLocationInCenter(Dimension size, @Nullable Point _default) {
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

    public void setDefaultButton(final JButton defaultButton) {
      //((JComponent)myPane).getRootPane().setDefaultButton(defaultButton);
      myDefaultButton = defaultButton;
    }
  }

  private static class MyRootPane extends JRootPane implements Disposable {
    private MyDialog myDialog;

    private MyRootPane(final MyDialog dialog) {
      myDialog = dialog;
    }

    protected JLayeredPane createLayeredPane() {
      JLayeredPane p = new JBLayeredPane();
      p.setName(this.getName()+".layeredPane");
      return p;
    }

    @Override
    public void dispose() {
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
  }

  public static class GlasspanePeerUnavailableException extends Exception {
  }

  public static class TransparentLayeredPane extends JBLayeredPane {
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
