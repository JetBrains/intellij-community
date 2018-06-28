/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.BalloonLayoutImpl;
import com.intellij.ui.mac.touchbar.TouchBarsManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextAccessor;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

public class WelcomeFrame extends JFrame implements IdeFrame, AccessibleContextAccessor {
  public static final ExtensionPointName<WelcomeFrameProvider> EP = ExtensionPointName.create("com.intellij.welcomeFrameProvider");
  static final String DIMENSION_KEY = "WELCOME_SCREEN";
  private static IdeFrame ourInstance;
  private static Disposable ourTouchbar;
  private final WelcomeScreen myScreen;
  private final BalloonLayout myBalloonLayout;

  public WelcomeFrame() {
    JRootPane rootPane = getRootPane();
    final WelcomeScreen screen = createScreen(rootPane);

    final IdeGlassPaneImpl glassPane = new IdeGlassPaneImpl(rootPane);
    setGlassPane(glassPane);
    glassPane.setVisible(false);
    setContentPane(screen.getWelcomePanel());
    setTitle(ApplicationNamesInfo.getInstance().getFullProductName());
    AppUIUtil.updateWindowIcon(this);

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
        dispose();
      }
    });

    myBalloonLayout = new BalloonLayoutImpl(rootPane, new Insets(8, 8, 8, 8));

    myScreen = screen;
    setupCloseAction(this);
    MnemonicHelper.init(this);
    myScreen.setupFrame(this);
    Disposer.register(ApplicationManager.getApplication(), new Disposable() {
      @Override
      public void dispose() {
        WelcomeFrame.this.dispose();
      }
    });
  }

  public static IdeFrame getInstance() {
    return ourInstance;
  }

  @Override
  public void dispose() {
    saveLocation(getBounds());

    super.dispose();

    Disposer.dispose(myScreen);

    resetInstance();
  }

  private static void saveLocation(Rectangle location) {
    Point middle = new Point(location.x + location.width / 2, location.y = location.height / 2);
    DimensionService.getInstance().setLocation(DIMENSION_KEY, middle, null);
  }

  static void setupCloseAction(final JFrame frame) {
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(
      new WindowAdapter() {
        public void windowClosing(final WindowEvent e) {
          if (ProjectManager.getInstance().getOpenProjects().length == 0) {
            ApplicationManagerEx.getApplicationEx().exit();
          }
          else {
            frame.dispose();
          }
        }
      });
  }

  private static WelcomeScreen createScreen(JRootPane rootPane) {
    WelcomeScreen screen = null;
    for (WelcomeScreenProvider provider : WelcomeScreenProvider.EP_NAME.getExtensions()) {
      if (!provider.isAvailable()) continue;
      screen = provider.createWelcomeScreen(rootPane);
      if (screen != null) break;
    }
    if (screen == null) {
      screen = new NewWelcomeScreen();
    }
    return screen;
  }
  
  public static void resetInstance() {
    ourInstance = null;
    if (ourTouchbar != null) {
      ourTouchbar.dispose();
      ourTouchbar = null;
    }
  }

  public static void showNow() {
    if (ourInstance != null) return;
    if (!GeneralSettings.getInstance().isShowWelcomeScreen()) {
      ApplicationManagerEx.getApplicationEx().exit(false, true);
    }

    IdeFrame frame = null;
    for (WelcomeFrameProvider provider : EP.getExtensions()) {
      frame = provider.createFrame();
      if (frame != null) break;
    }
    if (frame == null) {
      frame = new WelcomeFrame();
    }
    IdeMenuBar.installAppMenuIfNeeded((JFrame)frame);
    ((JFrame)frame).setVisible(true);
    ourInstance = frame;
    ourTouchbar = TouchBarsManager.showDialogWrapperButtons(frame.getComponent());
  }

  public static void showIfNoProjectOpened() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    ApplicationManager.getApplication().invokeLater((DumbAwareRunnable)() -> {
      WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
      windowManager.disposeRootFrame();
      IdeFrameImpl[] frames = windowManager.getAllProjectFrames();
      if (frames.length == 0) {
        showNow();
      }
    }, ModalityState.NON_MODAL);
  }

  @Override
  public StatusBar getStatusBar() {
    Container pane = getContentPane();
    //noinspection ConstantConditions
    return pane instanceof JComponent ? UIUtil.findComponentOfType((JComponent)pane, IdeStatusBarImpl.class) : null;
  }

  @Override
  public BalloonLayout getBalloonLayout() {
    return myBalloonLayout;
  }

  @Override
  public Rectangle suggestChildFrameBounds() {
    return getBounds();
  }

  @Nullable
  @Override
  public Project getProject() {
    return ProjectManager.getInstance().getDefaultProject();
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

  @Override
  public AccessibleContext getCurrentAccessibleContext() {
    return accessibleContext;
  }
}
