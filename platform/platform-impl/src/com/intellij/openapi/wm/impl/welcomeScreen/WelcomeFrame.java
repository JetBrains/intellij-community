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

/*
 * @author max
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ScreenUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

public class WelcomeFrame extends JFrame implements IdeFrame {
  private static final String DIMENSION_KEY = "WELCOME_SCREEN";
  private static WelcomeFrame ourInstance;
  private final WelcomeScreen myScreen;

  public WelcomeFrame() {
    JRootPane rootPane = getRootPane();
    final WelcomeScreen screen = createScreen(rootPane);

    final IdeGlassPaneImpl glassPane = new IdeGlassPaneImpl(rootPane);
    setGlassPane(glassPane);
    glassPane.setVisible(false);
    setContentPane(screen.getWelcomePanel());
    setTitle(ApplicationNamesInfo.getInstance().getFullProductName());
    AppUIUtil.updateFrameIcon(this);

    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public void projectOpened(Project project) {
        dispose();
      }
    });

    myScreen = screen;
    setupCloseAction();
    new MnemonicHelper().register(this);

    setResizable(false);
  }

  public static WelcomeFrame getInstance() {
    return ourInstance;
  }

  @Override
  public void dispose() {
    saveLocation(getBounds());

    super.dispose();

    Disposer.dispose(myScreen);

    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourInstance = null;
  }

  private static void saveLocation(Rectangle location) {
    Point middle = new Point(location.x + location.width / 2, location.y = location.height / 2);
    DimensionService.getInstance().setLocation(DIMENSION_KEY, middle, null);
  }

  private void setupCloseAction() {
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(
      new WindowAdapter() {
        public void windowClosing(final WindowEvent e) {
          dispose();

          final Application app = ApplicationManager.getApplication();
          app.invokeLater(new DumbAwareRunnable() {
            public void run() {
              if (app.isDisposed()) {
                ApplicationManagerEx.getApplicationEx().exit();
                return;
              }

              final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
              if (openProjects.length == 0) {
                ApplicationManagerEx.getApplicationEx().exit();
              }
            }
          }, ModalityState.NON_MODAL);
        }
      }
    );
  }

  public static void clearRecents() {
    if (ourInstance != null) {
      WelcomeScreen screen = ourInstance.myScreen;
      if (screen instanceof DefaultWelcomeScreen) {
        ((DefaultWelcomeScreen)screen).hideRecentProjectsPanel();
      }
    }
  }

  private static WelcomeScreen createScreen(JRootPane rootPane) {
    WelcomeScreen screen = null;
    for (WelcomeScreenProvider provider : WelcomeScreenProvider.EP_NAME.getExtensions()) {
      if (!provider.isAvailable()) continue;
      screen = provider.createWelcomeScreen(rootPane);
      if (screen != null) break;
    }
    if (screen == null) {
      //screen = new DefaultWelcomeScreen(rootPane);
      screen = new NewWelcomeScreen(rootPane);
    }
    return screen;
  }


  public static void showNow() {
    if (ourInstance == null) {
      WelcomeFrame frame = new WelcomeFrame();
      frame.pack();
      Point location = DimensionService.getInstance().getLocation(DIMENSION_KEY, null);
      Rectangle screenBounds = ScreenUtil.getScreenRectangle(location != null ? location : new Point(0, 0));
      frame.setLocation(new Point(
        screenBounds.x + (screenBounds.width - frame.getWidth()) / 2,
        screenBounds.y + (screenBounds.height - frame.getHeight()) / 3
      ));
      frame.setVisible(true);

      ourInstance = frame;
    }
  }

  public static void showIfNoProjectOpened() {
    ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
      @Override
      public void run() {
        IdeFrameImpl[] frames = ((WindowManagerImpl)WindowManager.getInstance()).getAllProjectFrames();
        if (frames.length == 0) {
          showNow();
        }
      }
    }, ModalityState.NON_MODAL);
  }

  @Override
  public StatusBar getStatusBar() {
    return null;
  }

  @Override
  public Rectangle suggestChildFrameBounds() {
    return getBounds();
  }

  @Nullable
  @Override
  public Project getProject() {
    return null;
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
  public JComponent getComponent() {
    return getRootPane();
  }

  public static void notifyFrameClosed(JFrame frame) {
    saveLocation(frame.getBounds());
  }
}
