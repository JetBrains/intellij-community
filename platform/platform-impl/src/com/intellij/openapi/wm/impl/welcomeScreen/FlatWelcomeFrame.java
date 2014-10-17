/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.*;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.InputStream;
import java.net.URL;

/**
 * @author Konstantin Bulenkov
 */
public class FlatWelcomeFrame extends JFrame implements WelcomeFrameProvider, IdeFrame {
  private final BalloonLayout myBalloonLayout;
  private final FlatWelcomeScreen myScreen;

  public FlatWelcomeFrame() {
    JRootPane rootPane = getRootPane();
    myScreen = new FlatWelcomeScreen();

    final IdeGlassPaneImpl glassPane = new IdeGlassPaneImpl(rootPane);
    setGlassPane(glassPane);
    glassPane.setVisible(false);
    setUndecorated(true);
    setContentPane(myScreen.getWelcomePanel());
    setTitle(ApplicationNamesInfo.getInstance().getFullProductName());
    AppUIUtil.updateWindowIcon(this);
    Rectangle bounds = ScreenUtil.getMainScreenBounds();
    setSize(666, 450);
    int x = bounds.x + (bounds.width - getWidth()) / 2;
    int y = bounds.y + (bounds.height - getHeight()) / 2;
    setLocation(x, y);
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public void projectOpened(Project project) {
        dispose();
      }
    });

    myBalloonLayout = new BalloonLayoutImpl(rootPane, new Insets(8, 8, 8, 8));

    setupCloseAction();
    new MnemonicHelper().register(this);
    Disposer.register(ApplicationManager.getApplication(), new Disposable() {
      @Override
      public void dispose() {
        FlatWelcomeFrame.this.dispose();
      }
    });
  }

  @Override
  public void dispose() {
    Disposer.dispose(myScreen);
    super.dispose();
    WelcomeFrame.resetInstance();
  }

  private static void saveLocation(Rectangle location) {
    Point middle = new Point(location.x + location.width / 2, location.y = location.height / 2);
    //DimensionService.getInstance().setLocation(DIMENSION_KEY, middle, null);
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

  @Override
  public StatusBar getStatusBar() {
    return null;
  }
  
  public static Color getMainBackground() {
    return Color.WHITE;
  }
  
  public static Color getProjectsBackGround() {
    return Gray._245;
  }

  @Override
  public IdeFrame createFrame() {
    return this;
  }

  private static class FlatWelcomeScreen extends JPanel implements WelcomeScreen {
    public FlatWelcomeScreen() {
      super(new BorderLayout());
      setBackground(getMainBackground());
      add(createRecentProjects(), BorderLayout.WEST);
      add(createBody(), BorderLayout.CENTER);
    }

    @Override
    public JComponent getWelcomePanel() {
      return this;
    }

    private JComponent createBody() {
      NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
      panel.add(createLogo(), BorderLayout.NORTH);
      panel.add(createActionPanel(), BorderLayout.CENTER);
      //panel.add(createSettingsAndDocs(), BorderLayout.SOUTH);
      return panel;
    }

    private JComponent createSettingsAndDocs() {
      return null;
    }

    private JComponent createActionPanel() {
      JPanel actions = new JPanel();
      actions.setOpaque(false);
      actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
      ActionManager actionManager = ActionManager.getInstance();
      ActionGroup quickStart = (ActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
      DefaultActionGroup group = new DefaultActionGroup();
      for (AnAction action : quickStart.getChildren(null)) {
        if (action instanceof ActionGroup) {
          group.addAll((ActionGroup)action);
        } else {
          group.add(action);
        }
      }

      // so, we sure this is the last action
      final AnAction register = actionManager.getAction("WelcomeScreen.Register");
      if (register != null) {
        group.add(register);
      }

      for (AnAction action : group.getChildren(null)) {
        JPanel button = new JPanel(new BorderLayout()) {
          @Override
          public Dimension getPreferredSize() {
            return new Dimension(250, super.getPreferredSize().height);
          }
        };
        button.setOpaque(false);
        button.setBorder(new EmptyBorder(4, 30, 0, 30));
        Presentation presentation = action.getTemplatePresentation();
        action.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(this),
                                        ActionPlaces.WELCOME_SCREEN, presentation, ActionManager.getInstance(), 0));
        if (presentation.isVisible()) {
          ActionLink link = new ActionLink(presentation.getText(), presentation.getIcon(), action);
          link.setBorder(new EmptyBorder(2, 5, 2, 5));
          installFocusable(link, action);
          button.add(link);
          actions.add(button);
        }

      }

      actions.setBorder(new EmptyBorder(0, 0, 0, 0));
      JPanel panel = new NonOpaquePanel(new BorderLayout());
      panel.add(actions, BorderLayout.NORTH);
      return panel;
    }

    private JComponent createLogo() {
      NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
      JLabel logo = new JLabel(IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getWelcomeScreenLogoUrl()));
      logo.setBorder(new EmptyBorder(20, 0, 0, 30));
      logo.setHorizontalAlignment(SwingConstants.CENTER);
      panel.add(logo, BorderLayout.NORTH);
      JLabel appName = new JLabel(ApplicationNamesInfo.getInstance().getFullProductName());
      Font font = getProductFont();
      appName.setFont(font.deriveFont(36f).deriveFont(Font.PLAIN));
      appName.setForeground(Gray._0);
      appName.setHorizontalAlignment(SwingConstants.CENTER);
      JLabel version = new JLabel("Version " + ApplicationInfoEx.getInstanceEx().getFullVersion());
      version.setFont(font.deriveFont(16f).deriveFont(Font.PLAIN));
      version.setHorizontalAlignment(SwingConstants.CENTER);
      version.setForeground(Gray._128);
      
      panel.add(appName);
      panel.add(version, BorderLayout.SOUTH);
      return panel;
    }

    private static Font getProductFont() {
      String name = "/fonts/Roboto-Light.ttf";
      URL url = AppUIUtil.class.getResource(name);
        if (url == null) {
          Logger.getInstance(AppUIUtil.class).warn("Resource missing: " + name);
        } else {

        try {
          InputStream is = url.openStream();
          try {
            return Font.createFont(Font.TRUETYPE_FONT, is);
          }
          finally {
            is.close();
          }
        }
        catch (Throwable t) {
          Logger.getInstance(AppUIUtil.class).warn("Cannot load font: " + url, t);
        }
      }
      return UIUtil.getLabelFont();
    }

    private JComponent createRecentProjects() {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(new NewRecentProjectPanel(this), BorderLayout.NORTH);
      panel.setBackground(getProjectsBackGround());
      return panel;
    }

    private void installFocusable(final JComponent comp, final AnAction action) {
      comp.setBorder(new EmptyBorder(2, 2, 2, 2));
      comp.setFocusable(true);
      comp.setFocusTraversalKeysEnabled(true);
      comp.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            action.actionPerformed(new AnActionEvent(e, 
                                                     DataManager.getInstance().getDataContext(), 
                                                     ActionPlaces.WELCOME_SCREEN, 
                                                     action.getTemplatePresentation().clone(), 
                                                     ActionManager.getInstance(), 
                                                     0));
          }
        }
      });
      comp.addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent e) {
          comp.setBorder(new CompoundBorder(new DottedBorder(new Insets(1, 1, 1, 1), Gray._128), new EmptyBorder(1,1,1,1)));
        }

        @Override
        public void focusLost(FocusEvent e) {
          comp.setBorder(new EmptyBorder(2, 2, 2, 2));
        }
      });

    }

    @Override
    public void setupFrame(JFrame frame) {

    }

    @Override
    public void dispose() {

    }
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

  public static void notifyFrameClosed(JFrame frame) {
    saveLocation(frame.getBounds());
  }
}
