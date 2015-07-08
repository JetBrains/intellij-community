/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBSlidingPanel;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.IconUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

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
    final int width = RecentProjectsManager.getInstance().getRecentProjectsActions(false).length == 0 ? 666 : 777;
    setSize(JBUI.size(width, 460));
    setResizable(false);
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

    myBalloonLayout = new BalloonLayoutImpl(rootPane, new JBInsets(8, 8, 8, 8));

    WelcomeFrame.setupCloseAction(this);
    MnemonicHelper.init(this);
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

  @Override
  public StatusBar getStatusBar() {
    return null;
  }

  public static Color getMainBackground() {
    return new JBColor(0xf7f7f7, 0x45474a);
  }

  public static Color getProjectsBackground() {
    return new JBColor(Gray.xFF, Gray.x39);
  }

  public static Color getLinkNormalColor() {
    return new JBColor(Gray._0, Gray.xBB);
  }

  public static Color getListSelectionColor(boolean hasFocus) {
    return hasFocus ? new JBColor(0x3875d6, 0x4b6eaf) : new JBColor(Gray.xDD, Gray.x45);
  }

  public static Color getActionLinkSelectionColor() {
    return new JBColor(0xdbe5f5, 0x485875);
  }

  public static JBColor getSeparatorColor() {
    return new JBColor(Gray.xEC, new Color(72, 75, 78));
  }

  private class FlatWelcomeScreen extends JPanel implements WelcomeScreen {
    private JBSlidingPanel mySlidingPanel = new JBSlidingPanel();

    public FlatWelcomeScreen() {
      super(new BorderLayout());
      mySlidingPanel.add("root", this);
      setBackground(getMainBackground());
      if (RecentProjectsManager.getInstance().getRecentProjectsActions(false, isUseProjectGroups()).length > 0) {
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
              if (RecentProjectsManager.getInstance().getRecentProjectsActions(false, isUseProjectGroups()).length == 0) {
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
          projectsList.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
              projectsList.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
              projectsList.repaint();
            }
          });
        }
      }
      add(createBody(), BorderLayout.CENTER);
    }

    @Override
    public JComponent getWelcomePanel() {
      return mySlidingPanel;
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
          button.setBorder(JBUI.Borders.empty(4, 10, 4, 10));
          button.add(registerLink);
          installFocusable(button, register, KeyEvent.VK_UP, KeyEvent.VK_RIGHT, true);
          NonOpaquePanel wrap = new NonOpaquePanel();
          wrap.setBorder(JBUI.Borders.empty(0, 10, 0, 0));
          wrap.add(button);
          panel.add(wrap, BorderLayout.WEST);
          registeredVisible = true;
        }
      }

      toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
      toolbar.add(createActionLink("Configure", IdeActions.GROUP_WELCOME_SCREEN_CONFIGURE, AllIcons.General.GearPlain, !registeredVisible));
      toolbar.add(createActionLink("Get Help", IdeActions.GROUP_WELCOME_SCREEN_DOC, null, false));

      panel.add(toolbar, BorderLayout.EAST);


      panel.setBorder(JBUI.Borders.empty(0, 0, 8, 11));
      return panel;
    }

    private JComponent createActionLink(final String text, final String groupId, Icon icon, boolean focusListOnLeft) {
      final Ref<ActionLink> ref = new Ref<ActionLink>(null);
      AnAction action = new AnAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ActionGroup configureGroup = (ActionGroup)ActionManager.getInstance().getAction(groupId);
          final PopupFactoryImpl.ActionGroupPopup popup = (PopupFactoryImpl.ActionGroupPopup)JBPopupFactory.getInstance()
            .createActionGroupPopup(null, new IconsFreeActionGroup(configureGroup), e.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false,
                                    ActionPlaces.WELCOME_SCREEN);
          popup.showUnderneathOfLabel(ref.get());
          UsageTrigger.trigger("welcome.screen." + groupId);
        }
      };
      ref.set(new ActionLink(text, icon, action));
      ref.get().setPaintUnderline(false);
      ref.get().setNormalColor(getLinkNormalColor());
      NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
      panel.setBorder(JBUI.Borders.empty(4, 6, 4, 6));
      panel.add(ref.get());
      panel.add(createArrow(ref.get()), BorderLayout.EAST);
      installFocusable(panel, action, KeyEvent.VK_UP, KeyEvent.VK_DOWN, focusListOnLeft);
      return panel;
    }

    private JComponent createActionPanel() {
      JPanel actions = new NonOpaquePanel();
      actions.setBorder(JBUI.Borders.empty(0, 10, 0, 0));
      actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
      ActionManager actionManager = ActionManager.getInstance();
      ActionGroup quickStart = (ActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
      DefaultActionGroup group = new DefaultActionGroup();
      collectAllActions(group, quickStart);

      for (AnAction action : group.getChildren(null)) {
        JPanel button = new JPanel(new BorderLayout());
        button.setOpaque(false);
        button.setBorder(JBUI.Borders.empty(8, 20, 8, 20));
        Presentation presentation = action.getTemplatePresentation();
        action.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(this),
                                        ActionPlaces.WELCOME_SCREEN, presentation, ActionManager.getInstance(), 0));
        if (presentation.isVisible()) {
          String text = presentation.getText();
          if (text.endsWith("...")) {
            text = text.substring(0, text.length() - 3);
          }
          Icon icon = presentation.getIcon();
          if (icon.getIconHeight() != JBUI.scale(16) || icon.getIconWidth() != JBUI.scale(16)) {
            icon = JBUI.emptyIcon(16);
          }
          action = wrapGroups(action);
          ActionLink link = new ActionLink(text, icon, action, createUsageTracker(action));
          link.setPaintUnderline(false);
          link.setNormalColor(getLinkNormalColor());
          button.add(link);
          if (action instanceof WelcomePopupAction) {
            button.add(createArrow(link), BorderLayout.EAST);
          }
          installFocusable(button, action, KeyEvent.VK_UP, KeyEvent.VK_DOWN, true);
          actions.add(button);
        }
      }

      WelcomeScreenActionsPanel panel = new WelcomeScreenActionsPanel();
      panel.actions.add(actions);
      return panel.root;
    }

    private AnAction wrapGroups(AnAction action) {
      if (action instanceof ActionGroup && ((ActionGroup)action).isPopup()) {
        final Pair<JPanel, JBList> panel = createActionGroupPanel((ActionGroup)action, mySlidingPanel, new Runnable() {
          @Override
          public void run() {
            goBack();
          }
        });
        final Runnable onDone = new Runnable() {
          @Override
          public void run() {
            final JBList list = panel.second;
            ListScrollingUtil.ensureSelectionExists(list);
            final ListSelectionListener[] listeners =
              ((DefaultListSelectionModel)list.getSelectionModel()).getListeners(ListSelectionListener.class);

            //avoid component cashing. This helps in case of LaF change
            for (ListSelectionListener listener : listeners) {
              listener.valueChanged(new ListSelectionEvent(list, list.getSelectedIndex(), list.getSelectedIndex(), true));
            }
            list.requestFocus();
          }
        };
        final String name = action.getClass().getName();
        mySlidingPanel.add(name, panel.first);
        final Presentation p = action.getTemplatePresentation();
        return new DumbAwareAction(p.getText(), p.getDescription(), p.getIcon()) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            mySlidingPanel.getLayout().swipe(mySlidingPanel, name, JBCardLayout.SwipeDirection.FORWARD, onDone);
          }
        };
      }
      return action;
    }

    protected void goBack() {
      mySlidingPanel.swipe("root", JBCardLayout.SwipeDirection.BACKWARD).doWhenDone(new Runnable() {
        @Override
        public void run() {
          mySlidingPanel.getRootPane().setDefaultButton(null);
        }
      });
    }

    private void collectAllActions(DefaultActionGroup group, ActionGroup actionGroup) {
      for (AnAction action : actionGroup.getChildren(null)) {
        if (action instanceof ActionGroup && !((ActionGroup)action).isPopup()) {
          collectAllActions(group, (ActionGroup)action);
        } else {
          group.add(action);
        }
      }
    }

    private JComponent createLogo() {
      NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
      ApplicationInfoEx app = ApplicationInfoEx.getInstanceEx();
      JLabel logo = new JLabel(IconLoader.getIcon(app.getWelcomeScreenLogoUrl()));
      logo.setBorder(JBUI.Borders.empty(30,0,10,0));
      logo.setHorizontalAlignment(SwingConstants.CENTER);
      panel.add(logo, BorderLayout.NORTH);
      JLabel appName = new JLabel(ApplicationNamesInfo.getInstance().getFullProductName());
      Font font = getProductFont();
      appName.setForeground(JBColor.foreground());
      appName.setFont(font.deriveFont(JBUI.scale(36f)).deriveFont(Font.PLAIN));
      appName.setHorizontalAlignment(SwingConstants.CENTER);
      String appVersion = "Version " + app.getFullVersion();

      if (app.isEAP() && app.getBuild().getBuildNumber() < Integer.MAX_VALUE) {
        appVersion += " (" + app.getBuild().asString() + ")";
      }

      JLabel version = new JLabel(appVersion);
      version.setFont(getProductFont().deriveFont(JBUI.scale(16f)));
      version.setHorizontalAlignment(SwingConstants.CENTER);
      version.setForeground(Gray._128);

      panel.add(appName);
      panel.add(version, BorderLayout.SOUTH);
      panel.setBorder(JBUI.Borders.empty(0, 0, 20, 0));
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
      panel.setBackground(getProjectsBackground());
      panel.setBorder(new CustomLineBorder(getSeparatorColor(), JBUI.insets(0, 0, 0, 1)));
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
            InputEvent event = e;
            if (e.getComponent() instanceof JComponent) {
              ActionLink link = UIUtil.findComponentOfType((JComponent)e.getComponent(), ActionLink.class);
              if (link != null) {
                event = new MouseEvent(link, MouseEvent.MOUSE_CLICKED, e.getWhen(), e.getModifiers(), 0, 0, 1, false, MouseEvent.BUTTON1);
              }
            }
            action.actionPerformed(new AnActionEvent(event,
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
          comp.setBackground(getActionLinkSelectionColor());
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
        super(group.getTemplatePresentation().getText(), group.getTemplatePresentation().getDescription(), null);
        myGroup = group;
      }

      @Override
      public boolean isPopup() {
        return myGroup.isPopup();
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
            UsageTrigger.trigger("welcome.screen." + e.getActionManager().getId(child));
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

  public static boolean isUseProjectGroups() {
    return Registry.is("welcome.screen.project.grouping.enabled");
  }

  private static Runnable createUsageTracker(final AnAction action) {
    return new Runnable() {
      @Override
      public void run() {
        UsageTrigger.trigger("welcome.screen." + ActionManager.getInstance().getId(action));
      }
    };
  }

  private static JLabel createArrow(final ActionLink link) {
    JLabel arrow = new JLabel(AllIcons.General.Combo3);
    arrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    arrow.setVerticalAlignment(SwingConstants.BOTTOM);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        final MouseEvent newEvent = new MouseEvent(link, e.getID(), e.getWhen(), e.getModifiers(), e.getX(), e.getY(), e.getClickCount(),
                                                   e.isPopupTrigger(), e.getButton());
        link.doClick(newEvent);
        return true;
      }
    }.installOn(arrow);
    return arrow;
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

  public static Pair<JPanel, JBList> createActionGroupPanel(ActionGroup action, final JComponent parent, final Runnable backAction) {
    JPanel actionsListPanel = new JPanel(new BorderLayout());
    actionsListPanel.setBackground(getProjectsBackground());
    final JBList list = new JBList(action.getChildren(null));
    list.setBackground(getProjectsBackground());
    list.installCellRenderer(new NotNullFunction<AnAction, JComponent>() {
      final JLabel label = new JLabel();
      Map<Icon, Icon> scaled = new HashMap<Icon, Icon>();

      {
        label.setBorder(JBUI.Borders.empty(3, 7, 3, 7));
      }

      @NotNull
      @Override
      public JComponent fun(AnAction action) {
        label.setText(action.getTemplatePresentation().getText());
        Icon icon = action.getTemplatePresentation().getIcon();
        if (icon.getIconHeight() == 32) {
          Icon scaledIcon = scaled.get(icon);
          if (scaledIcon == null) {
            scaledIcon = IconUtil.scale(icon, 0.5);
            scaled.put(icon, scaledIcon);
          }
          icon = scaledIcon;
        }
        label.setIcon(icon);
        return label;
      }
    });
    JScrollPane pane = ScrollPaneFactory.createScrollPane(list, true);
    pane.setBackground(getProjectsBackground());
    actionsListPanel.add(pane, BorderLayout.CENTER);
    if (backAction != null) {
      final JLabel back = new JLabel(AllIcons.Actions.Back);
      back.setBorder(JBUI.Borders.empty(3, 7, 10, 7));
      back.setHorizontalAlignment(SwingConstants.LEFT);
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent event, int clickCount) {
          backAction.run();
          return true;
        }
      }.installOn(back);
      actionsListPanel.add(back, BorderLayout.SOUTH);
    }
    final Ref<Component> selected = Ref.create();
    final JPanel main = new JPanel(new BorderLayout());
    main.add(actionsListPanel, BorderLayout.WEST);

    ListSelectionListener selectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          // Update when a change has been finalized.
          // For instance, selecting an element with mouse fires two consecutive ListSelectionEvent events.
          return;
        }
        if (!selected.isNull()) {
          main.remove(selected.get());
        }
        Object value = list.getSelectedValue();
        if (value instanceof AbstractActionWithPanel) {
          JPanel panel = ((AbstractActionWithPanel)value).createPanel();
          panel.setBorder(JBUI.Borders.empty(7, 10, 7, 10));
          selected.set(panel);
          main.add(selected.get());

          for (JButton button : UIUtil.findComponentsOfType(main, JButton.class)) {
            if (button.getClientProperty(DialogWrapper.DEFAULT_ACTION) == Boolean.TRUE) {
              parent.getRootPane().setDefaultButton(button);
              break;
            }
          }

          main.revalidate();
          main.repaint();
        }
      }
    };
    list.addListSelectionListener(selectionListener);
    if (backAction != null) {
      new AnAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          backAction.run();
        }
      }.registerCustomShortcutSet(KeyEvent.VK_ESCAPE, 0, main);
    }
    return Pair.create(main, list);
  }
}
