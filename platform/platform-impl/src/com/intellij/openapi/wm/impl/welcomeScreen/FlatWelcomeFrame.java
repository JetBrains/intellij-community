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

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.RecentProjectsManager;
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
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.InputStream;
import java.net.URL;

/**
 * @author Konstantin Bulenkov
 */
public class FlatWelcomeFrame extends JFrame implements IdeFrame {
  private final BalloonLayout myBalloonLayout;
  private final FlatWelcomeScreen myScreen;

  public FlatWelcomeFrame() {
    final JRootPane rootPane = getRootPane();
    myScreen = new FlatWelcomeScreen();

    final IdeGlassPaneImpl glassPane = new IdeGlassPaneImpl(rootPane) {
      @Override
      public void addNotify() {
        super.addNotify();
        rootPane.remove(getProxyComponent());
      }
    };

    setGlassPane(glassPane);
    glassPane.setVisible(false);
    //setUndecorated(true);
    setContentPane(myScreen.getWelcomePanel());
    setTitle("Welcome to " + ApplicationNamesInfo.getInstance().getFullProductName());
    AppUIUtil.updateWindowIcon(this);
    //Rectangle bounds = ScreenUtil.getMainScreenBounds();
    if (RecentProjectsManager.getInstance().getRecentProjectsActions(false).length > 0) {
      setSize(666, 460);
    } else {
      setSize(555, 460);
    }
    //int x = bounds.x + (bounds.width - getWidth()) / 2;
    //int y = bounds.y + (bounds.height - getHeight()) / 2;
    Point location = DimensionService.getInstance().getLocation(WelcomeFrame.DIMENSION_KEY, null);
    Rectangle screenBounds = ScreenUtil.getScreenRectangle(location != null ? location : new Point(0, 0));
    setLocation(new Point(
      screenBounds.x + (screenBounds.width - getWidth()) / 2,
      screenBounds.y + (screenBounds.height - getHeight()) / 3
    ));

    //setLocation(x, y);
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
    saveLocation(getBounds());
    super.dispose();
    Disposer.dispose(myScreen);
    WelcomeFrame.resetInstance();
  }

  private static void saveLocation(Rectangle location) {
    Point middle = new Point(location.x + location.width / 2, location.y = location.height / 2);
    DimensionService.getInstance().setLocation(WelcomeFrame.DIMENSION_KEY, middle, null);
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
    return new JBColor(new Color(0xf5f6f8), new Color(0x3c3f41));
  }
  
  public static Color getProjectsBackGround() {
    return new JBColor(Gray.xFF, new Color(58, 61, 63));
  }
  
  public static Color getLinkNormalColor() {
    return new JBColor(Gray._0, Gray.xBB);
  }
  
  private class FlatWelcomeScreen extends JPanel implements WelcomeScreen {
    public FlatWelcomeScreen() {
      super(new BorderLayout());
      setBackground(getMainBackground());
      if (RecentProjectsManager.getInstance().getRecentProjectsActions(false).length > 0) {
        final JComponent recentProjects = createRecentProjects();
        add(recentProjects, BorderLayout.WEST);
        final JList projectsList = UIUtil.findComponentOfType(recentProjects, JList.class);
        if (projectsList != null) {
          projectsList.getModel().addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {             
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
              removeIfNeeded();
            }

            private void removeIfNeeded() {
              if (projectsList.getModel().getSize() == 0) {
                FlatWelcomeScreen.this.remove(recentProjects);
                FlatWelcomeScreen.this.revalidate();
                FlatWelcomeScreen.this.repaint();
              }
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
              removeIfNeeded();
            }
          });
        }
      }
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
      panel.add(createSettingsAndDocs(), BorderLayout.SOUTH);
      return panel;
    }

    private JComponent createSettingsAndDocs() {
      JPanel panel = new NonOpaquePanel(new BorderLayout());
      NonOpaquePanel toolbar = new NonOpaquePanel();
      AnAction register = ActionManager.getInstance().getAction("Register");
      boolean registeredVisible = false;
      if (register != null) {
        Presentation presentation = register.getTemplatePresentation();
        register.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(this),
                                        ActionPlaces.WELCOME_SCREEN, presentation, ActionManager.getInstance(), 0));
        if (presentation.isEnabled()) {
          ActionLink registerLink = new ActionLink("Register", register);
          registerLink.setNormalColor(getLinkNormalColor());
          NonOpaquePanel button = new NonOpaquePanel(new BorderLayout());
          button.setBorder(new EmptyBorder(4, 10, 4, 10));
          button.add(registerLink);
          installFocusable(button, register, KeyEvent.VK_UP, KeyEvent.VK_RIGHT, true);
          NonOpaquePanel wrap = new NonOpaquePanel();
          wrap.setBorder(new EmptyBorder(0, 10, 0, 0));
          wrap.add(button);
          panel.add(wrap, BorderLayout.WEST);
          registeredVisible = true;
        }
      }

      toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
      toolbar.add(createActionLink("Configure", IdeActions.GROUP_WELCOME_SCREEN_CONFIGURE, AllIcons.General.Settings, !registeredVisible));
      toolbar.add(createActionLink("Get Help", IdeActions.GROUP_WELCOME_SCREEN_DOC, null, false));
      
      panel.add(toolbar, BorderLayout.EAST);
      

      panel.setBorder(new EmptyBorder(0,0,8,21));
      return panel;
    }
    
    private JComponent createActionLink(final String text, final String groupId, Icon icon, boolean focusListOnLeft) {
      final Ref<ActionLink> settings = new Ref<ActionLink>(null);
      AnAction action = new AnAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ActionGroup configureGroup = (ActionGroup)ActionManager.getInstance().getAction(groupId);
          JBPopupFactory.getInstance()
            .createActionGroupPopup(text, new IconsFreeActionGroup(configureGroup), e.getDataContext(), false, false, false, null,
                                    10, null)
            .showUnderneathOf(settings.get());
        }
      };
      settings.set(new ActionLink(text, icon, action));
      settings.get().setPaintUnderline(false);
      settings.get().setNormalColor(getLinkNormalColor());
      NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
      panel.setBorder(new EmptyBorder(4, 10, 4, 10));
      panel.add(settings.get());
      JLabel arrow = new JLabel(AllIcons.General.Combo2);
      arrow.setVerticalAlignment(SwingConstants.BOTTOM);
      panel.add(arrow, BorderLayout.EAST);
      installFocusable(panel, action, KeyEvent.VK_UP, KeyEvent.VK_DOWN, focusListOnLeft);
      return panel;
    }

    private JComponent createActionPanel() {
      JPanel actions = new NonOpaquePanel();
      actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
      ActionManager actionManager = ActionManager.getInstance();
      ActionGroup quickStart = (ActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
      DefaultActionGroup group = new DefaultActionGroup();
      collectAllActions(group, quickStart);

      for (AnAction action : group.getChildren(null)) {
        JPanel button = new JPanel(new BorderLayout());
        button.setOpaque(false);
        button.setBorder(new EmptyBorder(8, 20, 8, 20));
        Presentation presentation = action.getTemplatePresentation();
        action.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(this),
                                        ActionPlaces.WELCOME_SCREEN, presentation, ActionManager.getInstance(), 0));
        if (presentation.isVisible()) {
          ActionLink link = new ActionLink(presentation.getText(), presentation.getIcon(), action);
          link.setPaintUnderline(false);
          link.setNormalColor(getLinkNormalColor());
          installFocusable(button, action, KeyEvent.VK_UP, KeyEvent.VK_DOWN, true);
          button.add(link);
          actions.add(button);
        }
      }

      actions.setBorder(new EmptyBorder(0, 0, 0, 0));
      WelcomeScreenActionsPanel panel = new WelcomeScreenActionsPanel();
      panel.actions.add(actions);
      return panel.root;
    }

    private void collectAllActions(DefaultActionGroup group, ActionGroup actionGroup) {
      for (AnAction action : actionGroup.getChildren(null)) {
        if (action instanceof ActionGroup) {
          collectAllActions(group, (ActionGroup)action);
        } else {
          group.add(action);
        }
      }
    }

    private JComponent createLogo() {
      NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
      JLabel logo = new JLabel(IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getWelcomeScreenLogoUrl()));
      logo.setHorizontalAlignment(SwingConstants.CENTER);
      panel.add(logo, BorderLayout.NORTH);
      JLabel appName = new JLabel(ApplicationNamesInfo.getInstance().getFullProductName());
      Font font = getProductFont();
      appName.setForeground(JBColor.foreground());
      appName.setFont(font.deriveFont(36f).deriveFont(Font.PLAIN));
      appName.setHorizontalAlignment(SwingConstants.CENTER);
      JLabel version = new JLabel("Version " + ApplicationInfoEx.getInstanceEx().getFullVersion());
      version.setFont(font.deriveFont(16f).deriveFont(Font.PLAIN));
      version.setHorizontalAlignment(SwingConstants.CENTER);
      version.setForeground(Gray._128);
      
      panel.add(appName);
      panel.add(version, BorderLayout.SOUTH);
      panel.setBorder(new EmptyBorder(20, 10, 30, 10));
      return panel;
    }

    private Font getProductFont() {
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
      panel.add(new NewRecentProjectPanel(this), BorderLayout.CENTER);
      panel.setBackground(getProjectsBackGround());
      panel.setBorder(new CustomLineBorder(new JBColor(Gray.xEC, new Color(0x3c3f41)), 0,0,0,1));
      return panel;
    }

    private void installFocusable(final JComponent comp, final AnAction action, final int prevKeyCode, final int nextKeyCode, final boolean focusListOnLeft) {
      comp.setFocusable(true);
      comp.setFocusTraversalKeysEnabled(true);
      comp.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          final JList list = UIUtil.findComponentOfType(FlatWelcomeFrame.this.getComponent(), JList.class);
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            action.actionPerformed(new AnActionEvent(e,
                                                     DataManager.getInstance().getDataContext(),
                                                     ActionPlaces.WELCOME_SCREEN,
                                                     action.getTemplatePresentation().clone(),
                                                     ActionManager.getInstance(),
                                                     0));
          } else if (e.getKeyCode() == prevKeyCode) {
            focusPrev(comp);
          } else if (e.getKeyCode() == nextKeyCode) {
            focusNext(comp);
          } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
            if (focusListOnLeft) {
              if (list != null) {
                list.requestFocus();
              }  
            } else {
              focusPrev(comp);
            }
          } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
            focusNext(comp);
          }
        }
      });
      comp.addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent e) {
          comp.setOpaque(true);
          comp.setBackground(new JBColor(new Color(0xd2e1f0), new Color(0x455565)));
        }

        @Override
        public void focusLost(FocusEvent e) {
          comp.setOpaque(false);
          comp.setBackground(getMainBackground());
        }
      });

    }

    protected void focusPrev(JComponent comp) {
      FocusTraversalPolicy policy = FlatWelcomeFrame.this.getFocusTraversalPolicy();
      if (policy != null) {
        Component prev = policy.getComponentBefore(FlatWelcomeFrame.this, comp);
        if (prev != null) {
          prev.requestFocus();
        }
      }
    }

    protected void focusNext(JComponent comp) {
      FocusTraversalPolicy policy = FlatWelcomeFrame.this.getFocusTraversalPolicy();
      if (policy != null) {
        Component next = policy.getComponentAfter(FlatWelcomeFrame.this, comp);
        if (next != null) {
          next.requestFocus();
        }
      }
    }

    @Override
    public void setupFrame(JFrame frame) {

    }

    @Override
    public void dispose() {

    }

    private class IconsFreeActionGroup extends ActionGroup {
      private final ActionGroup myGroup;

      public IconsFreeActionGroup(ActionGroup group) {
        myGroup = group;
      }

      @NotNull
      @Override
      public AnAction[] getChildren(@Nullable AnActionEvent e) {
        AnAction[] children = myGroup.getChildren(e);
        AnAction[] patched = new AnAction[children.length];
        for (int i = 0; i < children.length; i++) {
          patched[i] = patch(children[i]);
        }
        return patched;
      }

      private AnAction patch(final AnAction child) {
        if (child instanceof ActionGroup) {
          return new IconsFreeActionGroup((ActionGroup)child);
        }
        
          Presentation presentation = child.getTemplatePresentation();
        return new AnAction(presentation.getText(),
                            presentation.getDescription(),
                            null) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            child.actionPerformed(e);
          }

          @Override
          public void update(@NotNull AnActionEvent e) {
            child.update(e);
            e.getPresentation().setIcon(null);
          }

          @Override
          public boolean isDumbAware() {
            return child.isDumbAware();
          }
        };
      }
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
  
  public static class WelcomeScreenActionsPanel {
    private JPanel root;
    private JPanel actions;
  }
}
