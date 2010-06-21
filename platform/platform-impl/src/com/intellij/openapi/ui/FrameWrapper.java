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
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.FocusTrackback;
import com.intellij.util.ImageLoader;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;

public class FrameWrapper implements Disposable {
  private String myDimensionKey = null;
  private JComponent myComponent = null;
  private JComponent myPreferedFocus = null;
  private String myTitle = "";
  private Image myImage = ImageLoader.loadFromResource(ApplicationInfoImpl.getShadowInstance().getIconUrl());
  private boolean myCloseOnEsc = false;
  private JFrame myFrame;
  private final Map<String, Object> myDatas = new HashMap<String, Object>();
  private Project myProject;
  private final ProjectManagerListener myProjectListener = new MyProjectManagerListener();
  private FocusTrackback myFocusTrackback;
  private FocusWatcher myFocusWatcher;

  private ActionCallback myFocusedCallback;
  private boolean isDisposed;

  public FrameWrapper() {
  }

  public FrameWrapper(@NonNls String dimensionServiceKey) {
    myDimensionKey = dimensionServiceKey;
  }

  public void setDimensionKey(String dimensionKey) {
    myDimensionKey = dimensionKey;
  }

  public void setData(String dataId, Object data) {
    myDatas.put(dataId, data);
  }

  public void setProject(Project project) {
    myProject = project;
    setData(PlatformDataKeys.PROJECT.getName(), project);
    ProjectManager.getInstance().addProjectManagerListener(project, myProjectListener);
  }

  public void show() {
    myFocusedCallback = new ActionCallback();

    if (myProject != null) {
      IdeFocusManager.getInstance(myProject).suspendKeyProcessingUntil(myFocusedCallback);
    }

    final JFrame frame = getFrame();

    myFocusTrackback = new FocusTrackback(this, null, true);

    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    WindowAdapter focusListener = new WindowAdapter() {
      public void windowOpened(WindowEvent e) {
        if (myPreferedFocus != null) {
          myPreferedFocus.requestFocusInWindow();
          myFocusTrackback.registerFocusComponent(myPreferedFocus);
        }

        myFocusedCallback.setDone();
      }
    };
    frame.addWindowListener(focusListener);
    if (myCloseOnEsc) addCloseOnEsc(frame);
    frame.getContentPane().add(myComponent, BorderLayout.CENTER);
    frame.setTitle(myTitle);
    frame.setIconImage(myImage);
    loadFrameState(myProject, myDimensionKey, frame);

    myFocusWatcher = new FocusWatcher() {
      protected void focusLostImpl(final FocusEvent e) {
        myFocusTrackback.consume();
      }
    };
    myFocusWatcher.install(myComponent);

    frame.setVisible(true);
  }

  public void close() {
    getFrame().setVisible(false);
    getFrame().dispose();
  }

  public void dispose() {
    isDisposed = true;
  }

  public boolean isDisposed() {
    return isDisposed;
  }

  private void addCloseOnEsc(final JFrame frame) {
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

  public JFrame getFrame() {
    assert !isDisposed : "Already disposed!";

    if (myFrame == null) {
      myFrame = new MyJFrame();
    }
    return myFrame;
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
    myImage = image;
  }

  private static void loadFrameState(Project project, String dimensionKey, JFrame frame) {
    final Point location;
    final Dimension size;
    final int extendedState;
    DimensionService dimensionService = DimensionService.getInstance();
    if (dimensionKey == null || dimensionService == null) {
      location = null;
      size = null;
      extendedState = -1;
    }
    else {
      location = dimensionService.getLocation(dimensionKey);
      size = dimensionService.getSize(dimensionKey);
      extendedState = dimensionService.getExtendedState(dimensionKey);
    }

    if (size != null && location != null) {
      frame.setLocation(location);
      frame.setSize(size);
      frame.getRootPane().revalidate();
    }
    else {
      frame.pack();
      frame.setBounds(WindowManagerEx.getInstanceEx().getIdeFrame(project).suggestChildFrameBounds());
    }

    if (extendedState == Frame.ICONIFIED || extendedState == Frame.MAXIMIZED_BOTH) {
      frame.setExtendedState(extendedState);
    }
  }

  private static void saveFrameState(String dimensionKey, JFrame frame) {
    DimensionService dimensionService = DimensionService.getInstance();
    if (dimensionKey == null || dimensionService == null) return;
    dimensionService.setLocation(dimensionKey, frame.getLocation());
    dimensionService.setSize(dimensionKey, frame.getSize());
    dimensionService.setExtendedState(dimensionKey, frame.getExtendedState());
  }

  public void setTitle(String title) {
    myTitle = title;
  }

  public void addDisposable(Disposable disposable) {
    Disposer.register(this, disposable);
  }

  private class MyJFrame extends JFrame implements DataProvider {
    private boolean myDisposing;

    private MyJFrame() throws HeadlessException {
    }

    public void dispose() {
      if (myDisposing) return;
      myDisposing = true;
      saveFrameState(myDimensionKey, this);
      Disposer.dispose(FrameWrapper.this);
      myDatas.clear();
      if (myProject != null) {
        ProjectManager.getInstance().removeProjectManagerListener(myProject, myProjectListener);
        myProject = null;
      }
      myPreferedFocus = null;

      myFocusTrackback.restoreFocus();
      if (myComponent != null && myFocusWatcher != null) {
        myFocusWatcher.deinstall(myComponent);
      }
      myFocusWatcher = null;
      myFocusedCallback = null;

      super.dispose();
    }

    public Object getData(String dataId) {
      return myDatas.get(dataId);
    }

    @Override
    public void paint(Graphics g) {
      UIUtil.applyRenderingHints(g);
      super.paint(g);
    }
  }

  private class MyProjectManagerListener extends ProjectManagerAdapter {
    public void projectClosing(Project project) {
      if (project == myProject) {
        close();
      }
    }
  }
}
