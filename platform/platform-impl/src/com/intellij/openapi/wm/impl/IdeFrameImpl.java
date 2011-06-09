/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.MouseGestureManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.status.EncodingPanel;
import com.intellij.openapi.wm.impl.status.InsertOverwritePanel;
import com.intellij.openapi.wm.impl.status.PositionPanel;
import com.intellij.openapi.wm.impl.status.ToggleReadOnlyAttributePanel;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.mac.MacMainFrameDecorator;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */

// Made non-final for Fabrique
public class IdeFrameImpl extends JFrame implements IdeFrame, DataProvider {
  private String myTitle;

  private String myFileTitle;
  private File myCurrentFile;

  private Project myProject;
  private final LayoutFocusTraversalPolicyExt myLayoutFocusTraversalPolicy;

  private IdeRootPane myRootPane;
  private final BalloonLayout myBalloonLayout;
  private static boolean myUpdatingTitle;
  private MacMainFrameDecorator myFrameDecorator;

  public IdeFrameImpl(ApplicationInfoEx applicationInfoEx, ActionManagerEx actionManager, UISettings uiSettings, DataManager dataManager,
                      final Application application, final String[] commandLineArgs) {
    super(applicationInfoEx.getFullApplicationName());
    myRootPane = new IdeRootPane(actionManager, uiSettings, dataManager, application, commandLineArgs, this);
    setRootPane(myRootPane);

    AppUIUtil.updateFrameIcon(this);
    final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    setBounds(10, 10, screenSize.width - 20, screenSize.height - 40);

    myLayoutFocusTraversalPolicy = new LayoutFocusTraversalPolicyExt();
    setFocusTraversalPolicy(myLayoutFocusTraversalPolicy);

    setupCloseAction();
    new MnemonicHelper().register(this);

    myBalloonLayout = new BalloonLayout(myRootPane.getLayeredPane(), new Insets(8, 8, 8, 8));

    if (!Registry.is("ide.windowSystem.focusAppOnStartup") && !isThereActiveFrame()) {
      setFocusableWindowState(false);
    }

    // to show window thumbnail under Macs
    // http://lists.apple.com/archives/java-dev/2009/Dec/msg00240.html
    if (SystemInfo.isMac) setIconImage(null); 
    
    MouseGestureManager.getInstance().add(this);
  }

  @Override
  public JComponent getComponent() {
    return getRootPane();
  }

  private static boolean isThereActiveFrame() {
    Frame[] all = Frame.getFrames();
    for (Frame each : all) {
      if (each.isActive()) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void show() {
    super.show();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        setFocusableWindowState(true);
      }
    });

    if (SystemInfo.isMac && myFrameDecorator == null) {
      myFrameDecorator = new MacMainFrameDecorator(this, PlatformUtils.isCidr());
    }
  }

  /**
   * This is overridden to get rid of strange Alloy LaF customization of frames. For unknown reason it sets the maxBounds rectangle
   * and it does it plain wrong. Setting bounds to <code>null</code> means default value should be taken from the underlying OS.
   */
  public synchronized void setMaximizedBounds(Rectangle bounds) {
    super.setMaximizedBounds(null);
  }

  private void setupCloseAction() {
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(
      new WindowAdapter() {
        public void windowClosing(final WindowEvent e) {
          final Application app = ApplicationManager.getApplication();
          app.invokeLater(new DumbAwareRunnable() {
            public void run() {
              if (app.isDisposed()) {
                ApplicationManagerEx.getApplicationEx().exit();
                return;
              }
              
              final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
              if (openProjects.length > 1) {
                if (myProject != null && myProject.isOpen()) {
                  ProjectUtil.closeAndDispose(myProject);
                }
                app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC).projectFrameClosed();
              }
              else {
                ApplicationManagerEx.getApplicationEx().exit();
              }
            }
          }, ModalityState.NON_MODAL);
        }
      }
    );
  }

  public StatusBar getStatusBar() {
    return ((IdeRootPane)getRootPane()).getStatusBar();
  }

  public void updateToolbar() {
    ((IdeRootPane)getRootPane()).updateToolbar();
  }

  public void updateMenuBar(){
    ((IdeRootPane)getRootPane()).updateMainMenuActions();
  }

  public void setTitle(final String title) {
    if (myUpdatingTitle) {
      super.setTitle(title);
    } else {
      myTitle = title;
    }

    updateTitle();
  }

  public void setFrameTitle(final String text) {
    super.setTitle(text);
  }

  public void setFileTitle(final String fileTitle) {
    setFileTitle(fileTitle, null);
  }

  public void setFileTitle(@Nullable final String fileTitle, @Nullable File file) {
    myFileTitle = fileTitle;
    myCurrentFile = file;
    updateTitle();
  }

  @Override
  public IdeRootPaneNorthExtension getNorthExtension(String key) {
    return myRootPane.findByName(key);
  }

  private void updateTitle() {
    updateTitle(this, myTitle, myFileTitle, myCurrentFile);
  }

  public static void updateTitle(JFrame frame, final String title, final String fileTitle, final File currentFile) {
    if (myUpdatingTitle) return;

    try {
      myUpdatingTitle = true;

      frame.getRootPane().putClientProperty("Window.documentFile", currentFile);

      final String applicationName = ((ApplicationInfoEx)ApplicationInfo.getInstance()).getFullApplicationName();
      final Builder builder = new Builder();
      if (SystemInfo.isMac) {
        builder.append(fileTitle).append(title)
          .append(ProjectManager.getInstance().getOpenProjects().length == 0 
                  || ((ApplicationInfoEx)ApplicationInfo.getInstance()).isEAP() && !applicationName.endsWith("SNAPSHOT") ? applicationName : null);
      } else {
        builder.append(title).append(fileTitle).append(applicationName);
      }
      
      frame.setTitle(builder.sb.toString());
    }
    finally {
      myUpdatingTitle = false;
    }
  }
  
  private static final class Builder {
    public StringBuilder sb = new StringBuilder();
    
    public Builder append(@Nullable final String s) {
      if (s == null || s.length() == 0) return this;
      if (sb.length() > 0) sb.append(" - ");
      sb.append(s);
      return this;
    }
  }

  public Object getData(final String dataId) {
    if (PlatformDataKeys.PROJECT.is(dataId)) {
      if (myProject != null) {
        return myProject.isInitialized() ? myProject : null;
      }
    }

    if (IdeFrame.KEY.getName().equals(dataId)) {
      return this;
    }

    return null;
  }

  public void setProject(final Project project) {
    myProject = project;
    if (project != null) {
      if (myRootPane != null) {
        myRootPane.installNorthComponents(project);
      }

      project.getMessageBus().connect().subscribe(StatusBar.Info.TOPIC, myRootPane.getStatusBar());
      installDefaultProjectStatusBarWidgets(myProject);
    }
    else {
      if (myRootPane != null) { //already disposed
        myRootPane.deinstallNorthComponents();
      }
    }

    if (project == null) {
      FocusTrackback.release(this);
    }
  }

  private void installDefaultProjectStatusBarWidgets(@NotNull final Project project) {
    final StatusBar statusBar = getStatusBar();

    final PositionPanel positionPanel = new PositionPanel(project);
    statusBar.addWidget(positionPanel, "before FatalError");

    final EncodingPanel encodingPanel = new EncodingPanel(project);
    statusBar.addWidget(encodingPanel, "after Position");

    final ToggleReadOnlyAttributePanel readOnlyAttributePanel = new ToggleReadOnlyAttributePanel();

    InsertOverwritePanel insertOverwritePanel = null;
    if (!SystemInfo.isMac) {
      insertOverwritePanel = new InsertOverwritePanel(project);
      statusBar.addWidget(insertOverwritePanel, "after Encoding");
      statusBar.addWidget(readOnlyAttributePanel, "after InsertOverwrite");
    } else {
      statusBar.addWidget(readOnlyAttributePanel, "after Encoding");
    }

    final InsertOverwritePanel finalInsertOverwritePanel = insertOverwritePanel;
    Disposer.register(project, new Disposable() {
      public void dispose() {
        statusBar.removeWidget(encodingPanel.ID());
        statusBar.removeWidget(positionPanel.ID());
        statusBar.removeWidget(readOnlyAttributePanel.ID());
        if (finalInsertOverwritePanel != null) statusBar.removeWidget(finalInsertOverwritePanel.ID());

        ((StatusBarEx)statusBar).removeCustomIndicationComponents();
      }
    });
  }

  public Project getProject() {
    return myProject;
  }

  public void dispose() {
    MouseGestureManager.getInstance().remove(this);

    if (myRootPane != null) {
      myRootPane = null;
    }

    if (myFrameDecorator != null) {
      myFrameDecorator.remove();
    }

    FocusTrackback.release(this);

    super.dispose();
  }

  @Override
  public void paint(Graphics g) {
    UIUtil.applyRenderingHints(g);
    super.paint(g);
  }

  public Rectangle suggestChildFrameBounds() {
//todo [kirillk] a dummy implementation
    final Rectangle b = getBounds();
    b.x += 100;
    b.width -= 200;
    b.y += 100;
    b.height -= 200;
    return b;
  }

  @Nullable
  public static Component findNearestModalComponent(@NotNull Component c) {
    Component eachParent = c;
    while (eachParent != null) {
      if (eachParent instanceof IdeFrame) return eachParent;
      if (eachParent instanceof JDialog) {
        if (((JDialog)eachParent).isModal()) return eachParent;
      }
      eachParent = eachParent.getParent();
    }

    return null;
  }

  public final BalloonLayout getBalloonLayout() {
    return myBalloonLayout;
  }
}
