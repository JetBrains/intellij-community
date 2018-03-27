// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.MouseGestureManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WindowStateService;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.FrameState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FrameWrapper implements Disposable, DataProvider {

  private String myDimensionKey = null;
  private JComponent myComponent = null;
  private JComponent myPreferredFocus = null;
  private String myTitle = "";
  private List<Image> myImages = null;
  private boolean myCloseOnEsc = false;
  private Window myFrame;
  private final Map<String, Object> myDataMap = ContainerUtil.newHashMap();
  private Project myProject;
  private final ProjectManagerListener myProjectListener = new MyProjectManagerListener();
  private FocusWatcher myFocusWatcher;

  private ActionCallback myFocusedCallback;
  private boolean myDisposing;
  private boolean myDisposed;

  protected StatusBar myStatusBar;
  private boolean myShown;
  private boolean myIsDialog;

  public FrameWrapper(Project project) {
    this(project, null);
  }

  public FrameWrapper(Project project, @Nullable @NonNls String dimensionServiceKey) {
    this(project, dimensionServiceKey, false);
  }

  public FrameWrapper(Project project, @Nullable @NonNls String dimensionServiceKey, boolean isDialog) {
    myDimensionKey = dimensionServiceKey;
    myIsDialog = isDialog;
    if (project != null) {
      setProject(project);
    }
  }

  public void setDimensionKey(String dimensionKey) {
    myDimensionKey = dimensionKey;
  }

  public void setData(String dataId, Object data) {
    myDataMap.put(dataId, data);
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

    if (frame instanceof JFrame) {
      ((JFrame)frame).setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    } else {
      ((JDialog)frame).setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    UIUtil.decorateFrame(((RootPaneContainer)frame).getRootPane());

    final WindowAdapter focusListener = new WindowAdapter() {
      @Override
      public void windowOpened(WindowEvent e) {
        IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
        JComponent toFocus = getPreferredFocusedComponent();
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
    if (Registry.is("ide.perProjectModality")) {
      frame.setAlwaysOnTop(true);
    }
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
    if (myImages != null) {
      // unwrap the image before setting as frame's icon
      frame.setIconImages(ContainerUtil.map(myImages, ImageUtil::toBufferedImage));
    }
    else {
      AppUIUtil.updateWindowIcon(myFrame);
    }

    if (restoreBounds) {
      loadFrameState();
    }

    myFocusWatcher = new FocusWatcher() {
      @Override
      protected void focusLostImpl(final FocusEvent e) {}
    };
    myFocusWatcher.install(myComponent);
    myShown = true;
    frame.setVisible(true);
  }

  public void close() {
    Disposer.dispose(this);
  }

  @Override
  public void dispose() {
    if (isDisposed()) return;

    Window frame = myFrame;
    StatusBar statusBar = myStatusBar;

    if (myShown && myDimensionKey != null) {
      WindowStateService.getInstance().saveStateFor(myProject, myDimensionKey, frame);
    }

    myFrame = null;
    myPreferredFocus = null;
    myProject = null;
    myDataMap.clear();

    if (myComponent != null && myFocusWatcher != null) {
      myFocusWatcher.deinstall(myComponent);
    }
    myFocusWatcher = null;
    myFocusedCallback = null;

    myComponent = null;
    myImages = null;
    myDisposed = true;

    if (statusBar != null) {
      Disposer.dispose(statusBar);
    }

    if (frame != null) {
      frame.setVisible(false);

      JRootPane rootPane = ((RootPaneContainer)frame).getRootPane();
      frame.removeAll();
      DialogWrapper.cleanupRootPane(rootPane);

      if (frame instanceof IdeFrame) {
        MouseGestureManager.getInstance().remove((IdeFrame)frame);
      }

      frame.dispose();

      DialogWrapper.cleanupWindowListeners(frame);
    }
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  private void addCloseOnEsc(final RootPaneContainer frame) {
    JRootPane rootPane = frame.getRootPane();
    ActionListener closeAction = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!PopupUtil.handleEscKeyEvent()) {
          // if you remove this line problems will start happen on Mac OS X
          // 2 projects opened, call Cmd+D on the second opened project and then Esc.
          // Weird situation: 2nd IdeFrame will be active, but focus will be somewhere inside the 1st IdeFrame
          // App is unusable until Cmd+Tab, Cmd+tab
          FrameWrapper.this.myFrame.setVisible(false);
          close();
        }
      }
    };
    rootPane.registerKeyboardAction(closeAction, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionUtil.registerForEveryKeyboardShortcut(rootPane, closeAction, CommonShortcuts.getCloseActiveWindow());
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
    return new MyJFrame(this, parent);
  }

  protected JDialog createJDialog(IdeFrame parent) {
    return new MyJDialog(this, parent);
  }

  protected IdeRootPaneNorthExtension getNorthExtension(String key) {
    return null;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    return null;
  }

  @Nullable
  private Object getDataInner(String dataId) {
    Object data = getData(dataId);
    return data != null ? data : myDataMap.get(dataId);
  }

  public void setComponent(JComponent component) {
    myComponent = component;
  }

  public void setPreferredFocusedComponent(JComponent preferedFocus) {
    myPreferredFocus = preferedFocus;
  }

  public JComponent getPreferredFocusedComponent() {
    return myPreferredFocus;
  }

  public void closeOnEsc() {
    myCloseOnEsc = true;
  }

  public void setImage(Image image) {
    setImages(image != null ? Collections.singletonList(image) : Collections.emptyList());
  }

  public void setImages(List<Image> images) {
    myImages = images;
  }

  protected void loadFrameState() {
    final Window frame = getFrame();
    if (myDimensionKey != null && !WindowStateService.getInstance().loadStateFor(myProject, myDimensionKey, frame)) {
      final IdeFrame ideFrame = WindowManagerEx.getInstanceEx().getIdeFrame(myProject);
      if (ideFrame != null) {
        frame.setBounds(ideFrame.suggestChildFrameBounds());
      }
    }
    ((RootPaneContainer)frame).getRootPane().revalidate();
  }

  public void setTitle(String title) {
    myTitle = title;
  }

  public void addDisposable(@NotNull Disposable disposable) {
    Disposer.register(this, disposable);
  }

  protected void setStatusBar(StatusBar statusBar) {
    if (myStatusBar != null) {
      Disposer.dispose(myStatusBar);
    }
    myStatusBar = statusBar;
  }

  private static class MyJFrame extends JFrame implements DataProvider, IdeFrame.Child {

    private FrameWrapper myOwner;
    private final IdeFrame myParent;

    private String myFrameTitle;
    private String myFileTitle;
    private File myFile;

    private MyJFrame(FrameWrapper owner, IdeFrame parent) throws HeadlessException {
      myOwner = owner;
      myParent = parent;
      FrameState.setFrameStateListener(this);
      setGlassPane(new IdeGlassPaneImpl(getRootPane(), true));

      boolean setMenuOnFrame = SystemInfo.isMac;

      if (SystemInfo.isLinux) {
        final String desktop = System.getenv("XDG_CURRENT_DESKTOP");
        if ("Unity".equals(desktop) || "Unity:Unity7".equals(desktop)) {
         try {
           Class.forName("com.jarego.jayatana.Agent");
           setMenuOnFrame = true;
         }
         catch (ClassNotFoundException e) {
           // ignore
         }
       }
      }

      if (setMenuOnFrame) {
        setJMenuBar(new IdeMenuBar(ActionManagerEx.getInstanceEx(), DataManager.getInstance()));
      }

      MouseGestureManager.getInstance().add(this);
      setFocusTraversalPolicy(new LayoutFocusTraversalPolicyExt());
      setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    @Override
    public JComponent getComponent() {
      return getRootPane();
    }

    @Override
    public StatusBar getStatusBar() {
      StatusBar ownerBar = myOwner != null ? myOwner.myStatusBar : null;
      return ownerBar != null ? ownerBar : myParent != null ? myParent.getStatusBar() : null;
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
      return myOwner.getNorthExtension(key);
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

    @Override
    public void dispose() {
      FrameWrapper owner = myOwner;
      myOwner = null;
      if (owner == null || owner.myDisposing) return;
      owner.myDisposing = true;
      Disposer.dispose(owner);
      super.dispose();
      rootPane = null;
      setMenuBar(null);
    }

    @Override
    public Object getData(String dataId) {
      if (IdeFrame.KEY.getName().equals(dataId)) {
        return this;
      }
      return myOwner == null ? null : myOwner.getDataInner(dataId);
    }

    @Override
    public void paint(Graphics g) {
      UISettings.setupAntialiasing(g);
      super.paint(g);
    }
  }

  private static class MyJDialog extends JDialog implements DataProvider, IdeFrame.Child {

    private FrameWrapper myOwner;
    private final IdeFrame myParent;

    private MyJDialog(FrameWrapper owner, IdeFrame parent) throws HeadlessException {
      super((JFrame)parent);
      myOwner = owner;
      myParent = parent;
      setGlassPane(new IdeGlassPaneImpl(getRootPane()));
      getRootPane().putClientProperty("Window.style", "small");
      setBackground(UIUtil.getPanelBackground());
      MouseGestureManager.getInstance().add(this);
      setFocusTraversalPolicy(new LayoutFocusTraversalPolicyExt());
      setDefaultCloseOperation(DISPOSE_ON_CLOSE);
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

    @Override
    public void dispose() {
      FrameWrapper owner = myOwner;
      myOwner = null;
      if (owner == null || owner.myDisposing) return;
      owner.myDisposing = true;
      Disposer.dispose(owner);
      super.dispose();
      rootPane = null;
    }

    @Override
    public Object getData(String dataId) {
      if (IdeFrame.KEY.getName().equals(dataId)) {
        return this;
      }
      return myOwner == null ? null : myOwner.getDataInner(dataId);
    }

    @Override
    public void paint(Graphics g) {
      UISettings.setupAntialiasing(g);
      super.paint(g);
    }
  }


  public void setLocation(Point location) {
    getFrame().setLocation(location);
  }

  public void setSize(Dimension size) {
    getFrame().setSize(size);
  }

  private class MyProjectManagerListener implements ProjectManagerListener {
    @Override
    public void projectClosing(Project project) {
      if (project == myProject) {
        close();
      }
    }
  }
}
