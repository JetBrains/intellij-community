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
  }

  public static WelcomeFrame getInstance() {
    return ourInstance;
  }

  @Override
  public void dispose() {
    super.dispose();

    Disposer.dispose(myScreen);

    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourInstance = null;
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
      screen = provider.createWelcomeScreen(rootPane);
      if (screen != null) break;
    }
    if (screen == null) {
      screen = new DefaultWelcomeScreen(rootPane);
    }
    return screen;
  }


  public static void showNow() {
    if (ourInstance == null) {
      WelcomeFrame frame = new WelcomeFrame();
      frame.pack();
      DimensionService dimensionService = DimensionService.getInstance();
      Point location = dimensionService.getLocation(DIMENSION_KEY);
      if (location == null) {
        Rectangle screenBounds = ScreenUtil.getScreenRectangle(new Point(0, 0));
        location = new Point(
          screenBounds.x + (screenBounds.width - frame.getWidth()) / 2,
          screenBounds.y + (screenBounds.height - frame.getHeight()) / 3
        );
      }
      frame.setLocation(location);
      dimensionService.setLocation(DIMENSION_KEY, location);
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
}
