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
package com.intellij.openapi.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.MouseGestureManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.FocusTrackback;
import com.intellij.util.ImageLoader;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Map;

public class FrameWrapper implements Disposable, DataProvider {
  private String myDimensionKey = null;
  private JComponent myComponent = null;
  private JComponent myPreferedFocus = null;
  private String myTitle = "";
  private Image myImage = ImageLoader.loadFromResource(ApplicationInfoImpl.getShadowInstance().getIconUrl());
  private boolean myCloseOnEsc = false;
  private Window myFrame;
  private final Map<String, Object> myDatas = new HashMap<String, Object>();
  private Project myProject;
  private final ProjectManagerListener myProjectListener = new MyProjectManagerListener();
  private FocusTrackback myFocusTrackback;
  private FocusWatcher myFocusWatcher;

  private ActionCallback myFocusedCallback;
  private boolean myDisposed;

  protected StatusBar myStatusBar;
  private boolean myShown;
  private boolean myIsDialog;
  private boolean myImageWasChanged;

  //Skip restoration of MAXIMIZED_BOTH_PROPERTY
  private static final boolean WORKAROUND_FOR_JDK_8007219 = SystemInfo.isMac && SystemInfo.isOracleJvm;

  public FrameWrapper(Project project) {
    this(project, null);
  }

  public FrameWrapper(Project project, @Nullable @NonNls String dimensionServiceKey) {
    this(project, dimensionServiceKey, false);
  }

  public FrameWrapper(Project project, @Nullable @NonNls String dimensionServiceKey, boolean isDialog) {
    myProject = project;
    myDimensionKey = dimensionServiceKey;
    myIsDialog = isDialog;
  }

  public void setDimensionKey(String dimensionKey) {
    myDimensionKey = dimensionKey;
  }

  public void setData(String dataId, Object data) {
    myDatas.put(dataId, data);
  }

  public void setProject(@NotNull final Project project) {
    myProject = project;
    setData(CommonDataKeys.PROJECT.getName(), project);
    ProjectManager.getInstance().addProjectManagerListener(project, myProjectListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        ProjectManager.getInstance().removeProjectManagerListener(project, myProjectListener);
      }
    });
  }

  public void show() {
    show(true);
  }

  public void show(boolean restoreBounds) {
    myFocusedCallback = new ActionCallback();

    if (myProject != null) {
      IdeFocusManager.getInstance(myProject).typeAheadUntil(myFocusedCallback);
    }

    final Window frame = getFrame();

    if (myStatusBar != null) {
      myStatusBar.install((IdeFrame)frame);
    }

    myFocusTrackback = new FocusTrackback(this, IdeFocusManager.findInstance().getFocusOwner(), true);

    if (frame instanceof JFrame) {
      ((JFrame)frame).setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    } else {
      ((JDialog)frame).setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }
    final WindowAdapter focusListener = new WindowAdapter() {
      public void windowOpened(WindowEvent e) {
        IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
        JComponent toFocus = myPreferedFocus;
        if (toFocus == null) {
          toFocus = fm.getFocusTargetFor(myComponent);
        }

        if (toFocus != null) {
          fm.requestFocus(toFocus, true).notify(myFocusedCallback);
        } else {
          myFocusedCallback.setRejected();
        }
      }
    };
    frame.addWindowListener(focusListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        frame.removeWindowListener(focusListener);
      }
    });
    if (myCloseOnEsc) addCloseOnEsc((RootPaneContainer)frame);
    ((RootPaneContainer)frame).getContentPane().add(myComponent, BorderLayout.CENTER);
    if (frame instanceof JFrame) {
      ((JFrame)frame).setTitle(myTitle);
    } else {
      ((JDialog)frame).setTitle(myTitle);
    }
    if (myImageWasChanged) {
      frame.setIconImage(myImage);
    }
    else {
      AppUIUtil.updateWindowIcon(myFrame);
    }

    if (restoreBounds) {
      loadFrameState();
    }

    myFocusWatcher = new FocusWatcher() {
      protected void focusLostImpl(final FocusEvent e) {
        myFocusTrackback.consume();
      }
    };
    myFocusWatcher.install(myComponent);
    myShown = true;
    frame.setVisible(true);

    if (UIUtil.isUnderAlloyLookAndFeel() && frame instanceof JFrame) {
      //please ask [kb] before remove it
      ((JFrame)frame).setMaximizedBounds(null);
    }
  }

  public void close() {
    Disposer.dispose(this);
  }

  public void dispose() {
    if (isDisposed()) return;

    Window frame = getFrame();

    final JRootPane rootPane = ((RootPaneContainer)frame).getRootPane();
    if (rootPane != null) {
      DialogWrapper.unregisterKeyboardActions(rootPane);
    }

    frame.setVisible(false);
    frame.dispose();

    if (frame instanceof JFrame) {
      FocusTrackback.release((JFrame)frame);
    }

    if (myStatusBar != null) {
      Disposer.dispose(myStatusBar);
      myStatusBar = null;
    }

    myDisposed = true;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  private void addCloseOnEsc(final RootPaneContainer frame) {
    frame.getRootPane().registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
          MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
          if (selectedPath.length > 0) { // hide popup menu if any
            menuSelectionManager.clearSelectedPath();
          }
          else {
            close();
          }
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW
    );
  }

  public Window getFrame() {
    assert !myDisposed : "Already disposed!";

    if (myFrame == null) {
      final IdeFrame parent = WindowManager.getInstance().getIdeFrame(myProject);
      myFrame = myIsDialog ? createJDialog(parent) : createJFrame(parent);
    }
    return myFrame;
  }

  protected JFrame createJFrame(IdeFrame parent) {
    return new MyJFrame(parent) {
      @Override
      public IdeRootPaneNorthExtension getNorthExtension(String key) {
        return FrameWrapper.this.getNorthExtension(key);
      }
    };
  }

  protected JDialog createJDialog(IdeFrame parent) {
    return new MyJDialog(parent);
  }

  protected IdeRootPaneNorthExtension getNorthExtension(String key) {
    return null;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    return null;
  }

  public void setComponent(JComponent component) {
    myComponent = component;
  }

  public void setPreferredFocusedComponent(JComponent preferedFocus) {
    myPreferedFocus = preferedFocus;
  }

  public void closeOnEsc() {
    myCloseOnEsc = true;
  }

  public void setImage(Image image) {
    myImageWasChanged = true;
    myImage = image;
  }

  protected void loadFrameState() {
    final Window frame = getFrame();
    final Point location;
    final Dimension size;
    final int extendedState;
    DimensionService dimensionService = DimensionService.getInstance();
    if (myDimensionKey == null || dimensionService == null) {
      location = null;
      size = null;
      extendedState = -1;
    }
    else {
      location = dimensionService.getLocation(myDimensionKey);
      size = dimensionService.getSize(myDimensionKey);
      extendedState = dimensionService.getExtendedState(myDimensionKey);
    }

    if (size != null && location != null) {
      frame.setLocation(location);
      frame.setSize(size);
      ((RootPaneContainer)frame).getRootPane().revalidate();
    }
    else {
      final IdeFrame ideFrame = WindowManagerEx.getInstanceEx().getIdeFrame(myProject);
      if (ideFrame != null) {
        frame.pack();
        frame.setBounds(ideFrame.suggestChildFrameBounds());
      }
    }

    if (!WORKAROUND_FOR_JDK_8007219 && extendedState == Frame.MAXIMIZED_BOTH && frame instanceof JFrame) {
      ((JFrame)frame).setExtendedState(extendedState);
    }
  }

  private static void saveFrameState(String dimensionKey, Component frame) {
    DimensionService dimensionService = DimensionService.getInstance();
    if (dimensionKey == null || dimensionService == null) return;
    dimensionService.setLocation(dimensionKey, frame.getLocation());
    dimensionService.setSize(dimensionKey, frame.getSize());
    if (frame instanceof JFrame) {
      dimensionService.setExtendedState(dimensionKey, ((JFrame)frame).getExtendedState());
    }
  }

  public void setTitle(String title) {
    myTitle = title;
  }

  public void addDisposable(Disposable disposable) {
    Disposer.register(this, disposable);
  }

  protected void setStatusBar(StatusBar statusBar) {
    if (myStatusBar != null) {
      Disposer.dispose(myStatusBar);
    }
    myStatusBar = statusBar;
  }

  private class MyJFrame extends JFrame implements DataProvider, IdeFrame.Child {

    private boolean myDisposing;
    private final IdeFrame myParent;

    private String myFrameTitle;
    private String myFileTitle;
    private File myFile;

    private MyJFrame(IdeFrame parent) throws HeadlessException {
      myParent = parent;
      setGlassPane(new IdeGlassPaneImpl(getRootPane()));

      if (SystemInfo.isMac) {
        setJMenuBar(new IdeMenuBar(ActionManagerEx.getInstanceEx(), DataManager.getInstance()));
      }

      MouseGestureManager.getInstance().add(this);
      setFocusTraversalPolicy(new LayoutFocusTraversalPolicyExt());
    }

    @Override
    public JComponent getComponent() {
      return getRootPane();
    }

    @Override
    public StatusBar getStatusBar() {
      return myStatusBar != null ? myStatusBar : myParent.getStatusBar();
    }

    @Override
    public Rectangle suggestChildFrameBounds() {
      return myParent.suggestChildFrameBounds();
    }

    @Override
    public Project getProject() {
      return myParent.getProject();
    }

    @Override
    public void setFrameTitle(String title) {
      myFrameTitle = title;
      updateTitle();
    }

    @Override
    public void setFileTitle(String fileTitle, File ioFile) {
      myFileTitle = fileTitle;
      myFile = ioFile;
      updateTitle();
    }

    @Override
    public IdeRootPaneNorthExtension getNorthExtension(String key) {
      return null;
    }

    @Override
    public BalloonLayout getBalloonLayout() {
      return null;
    }

    private void updateTitle() {
      IdeFrameImpl.updateTitle(this, myFrameTitle, myFileTitle, myFile);
    }

    @Override
    public IdeFrame getParentFrame() {
      return myParent;
    }

    public void dispose() {
      if (myDisposing) return;
      myDisposing = true;

      MouseGestureManager.getInstance().remove(this);

      if (myShown) {
        saveFrameState(myDimensionKey, this);
      }

      Disposer.dispose(FrameWrapper.this);
      myDatas.clear();
      myProject = null;
      myPreferedFocus = null;

      if (myFocusTrackback != null) {
        myFocusTrackback.restoreFocus();
      }
      if (myComponent != null && myFocusWatcher != null) {
        myFocusWatcher.deinstall(myComponent);
      }
      myFocusWatcher = null;
      myFocusedCallback = null;

      super.dispose();
    }

    public Object getData(String dataId) {
      if (IdeFrame.KEY.getName().equals(dataId)) {
        return this;
      }

      Object data = FrameWrapper.this.getData(dataId);
      return data != null ? data : myDatas.get(dataId);
    }

    @Override
    public void paint(Graphics g) {
      UIUtil.applyRenderingHints(g);
      super.paint(g);
    }
  }

  private class MyJDialog extends JDialog implements DataProvider, IdeFrame.Child {

    private boolean myDisposing;
    private final IdeFrame myParent;

    private MyJDialog(IdeFrame parent) throws HeadlessException {
      super((JFrame)parent);
      myParent = parent;
      setGlassPane(new IdeGlassPaneImpl(getRootPane()));
      getRootPane().putClientProperty("Window.style", "small");
      setBackground(UIUtil.getPanelBackground());
      MouseGestureManager.getInstance().add(this);
      setFocusTraversalPolicy(new LayoutFocusTraversalPolicyExt());
    }

    @Override
    public JComponent getComponent() {
      return getRootPane();
    }

    @Override
    public StatusBar getStatusBar() {
      return null;
    }

    @Nullable
    @Override
    public BalloonLayout getBalloonLayout() {
      return null;
    }

    @Override
    public Rectangle suggestChildFrameBounds() {
      return myParent.suggestChildFrameBounds();
    }

    @Override
    public Project getProject() {
      return myParent.getProject();
    }

    @Override
    public void setFrameTitle(String title) {
      setTitle(title);
    }

    @Override
    public void setFileTitle(String fileTitle, File ioFile) {
      setTitle(fileTitle);
    }

    @Override
    public IdeRootPaneNorthExtension getNorthExtension(String key) {
      return null;
    }

    @Override
    public IdeFrame getParentFrame() {
      return myParent;
    }

    public void dispose() {
      if (myDisposing) return;
      myDisposing = true;

      MouseGestureManager.getInstance().remove(this);

      if (myShown) {
        saveFrameState(myDimensionKey, this);
      }

      Disposer.dispose(FrameWrapper.this);
      myDatas.clear();
      myProject = null;
      myPreferedFocus = null;

      if (myFocusTrackback != null) {
        myFocusTrackback.restoreFocus();
      }
      if (myComponent != null && myFocusWatcher != null) {
        myFocusWatcher.deinstall(myComponent);
      }
      myFocusWatcher = null;
      myFocusedCallback = null;

      super.dispose();
    }

    public Object getData(String dataId) {
      if (IdeFrame.KEY.getName().equals(dataId)) {
        return this;
      }

      Object data = FrameWrapper.this.getData(dataId);
      return data != null ? data : myDatas.get(dataId);
    }

    @Override
    public void paint(Graphics g) {
      UIUtil.applyRenderingHints(g);
      super.paint(g);
    }
  }


  public void setLocation(Point location) {
    getFrame().setLocation(location);
  }

  public void setSize(Dimension size) {
    getFrame().setSize(size);
  }

  private class MyProjectManagerListener extends ProjectManagerAdapter {
    public void projectClosing(Project project) {
      if (project == myProject) {
        close();
      }
    }
  }
}
