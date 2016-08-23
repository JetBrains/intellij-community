/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.IdeNotificationArea;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.JBProtocolCommand;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
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
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.ParameterizedRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextAccessor;
import com.intellij.util.ui.accessibility.AccessibleContextDelegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
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
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class FlatWelcomeFrame extends JFrame implements IdeFrame, Disposable, AccessibleContextAccessor {
  private static final String ACTION_GROUP_KEY = "ACTION_GROUP_KEY";
  private BalloonLayout myBalloonLayout;
  private final FlatWelcomeScreen myScreen;
  private boolean myDisposed;

  public FlatWelcomeFrame() {
    final JRootPane rootPane = getRootPane();
    myScreen = new FlatWelcomeScreen();

    final IdeGlassPaneImpl glassPane = new IdeGlassPaneImpl(rootPane) {
      @Override
      public void addNotify() {
        super.addNotify();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> JBProtocolCommand.handleCurrentCommand());
      }
    };

    setGlassPane(glassPane);
    glassPane.setVisible(false);
    //setUndecorated(true);
    setContentPane(myScreen.getWelcomePanel());
    setTitle(getWelcomeFrameTitle());
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
        Disposer.dispose(FlatWelcomeFrame.this);
      }
    }, this);

    if (NotificationsManagerImpl.newEnabled()) {
      myBalloonLayout = new WelcomeBalloonLayoutImpl(rootPane, JBUI.insets(8), myScreen.myEventListener, myScreen.myEventLocation);
    }
    else {
      myBalloonLayout = new BalloonLayoutImpl(rootPane, JBUI.insets(8));
    }

    WelcomeFrame.setupCloseAction(this);
    MnemonicHelper.init(this);
    Disposer.register(ApplicationManager.getApplication(), this);
  }

  @Override
  public void dispose() {
    if (myDisposed) {
      return;
    }
    myDisposed = true;
    saveLocation(getBounds());
    super.dispose();
    if (myBalloonLayout != null) {
      ((BalloonLayoutImpl)myBalloonLayout).dispose();
      myBalloonLayout = null;
    }
    Disposer.dispose(myScreen);
    WelcomeFrame.resetInstance();

    // open project from welcome screen show progress dialog and call FocusTrackback.register()
    FocusTrackback.release(this);
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

  @Override
  public AccessibleContext getCurrentAccessibleContext() {
    return accessibleContext;
  }

  protected String getWelcomeFrameTitle() {
    return "Welcome to " + ApplicationNamesInfo.getInstance().getFullProductName();
  }

  private class FlatWelcomeScreen extends JPanel implements WelcomeScreen {
    private JBSlidingPanel mySlidingPanel = new JBSlidingPanel();
    public ParameterizedRunnable<List<NotificationType>> myEventListener;
    public Computable<Point> myEventLocation;

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
        AnActionEvent e =
          AnActionEvent.createFromAnAction(register, null, ActionPlaces.WELCOME_SCREEN, DataManager.getInstance().getDataContext(this));
        register.update(e);
        Presentation presentation = e.getPresentation();
        if (presentation.isEnabled()) {
          ActionLink registerLink = new ActionLink("Register", register);
          // Don't allow focus, as the containing panel is going to focusable.
          registerLink.setFocusable(false);
          registerLink.setNormalColor(getLinkNormalColor());
          NonOpaquePanel button = new NonOpaquePanel(new BorderLayout());
          button.setBorder(JBUI.Borders.empty(4, 10));
          button.add(registerLink);
          installFocusable(button, register, KeyEvent.VK_UP, KeyEvent.VK_RIGHT, true);
          NonOpaquePanel wrap = new NonOpaquePanel();
          wrap.setBorder(JBUI.Borders.emptyLeft(10));
          wrap.add(button);
          panel.add(wrap, BorderLayout.WEST);
          registeredVisible = true;
        }
      }

      toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
      if (NotificationsManagerImpl.newEnabled()) {
        toolbar.add(createEventsLink());
      }
      toolbar.add(createActionLink("Configure", IdeActions.GROUP_WELCOME_SCREEN_CONFIGURE, AllIcons.General.GearPlain, !registeredVisible));
      toolbar.add(createActionLink("Get Help", IdeActions.GROUP_WELCOME_SCREEN_DOC, null, false));

      panel.add(toolbar, BorderLayout.EAST);


      panel.setBorder(JBUI.Borders.empty(0, 0, 8, 11));
      return panel;
    }

    private JComponent createEventsLink() {
      final Ref<ActionLink> actionLinkRef = new Ref<>();
      final JComponent panel = createActionLink("Events", AllIcons.Ide.Notification.NoEvents, actionLinkRef, new AnAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ((WelcomeBalloonLayoutImpl)myBalloonLayout).showPopup();
        }
      });
      panel.setVisible(false);
      myEventListener = types -> {
        NotificationType type = null;
        for (NotificationType t : types) {
          if (NotificationType.ERROR == t) {
            type = NotificationType.ERROR;
            break;
          }
          if (NotificationType.WARNING == t) {
            type = NotificationType.WARNING;
          }
          else if (type == null && NotificationType.INFORMATION == t) {
            type = NotificationType.INFORMATION;
          }
        }
        if (types.isEmpty()) {
          panel.setVisible(false);
        }
        else {
          actionLinkRef.get().setIcon(IdeNotificationArea.createIconWithNotificationCount(actionLinkRef.get(), type, types.size()));
          panel.setVisible(true);
        }
      };
      myEventLocation = () -> {
        Point location = SwingUtilities.convertPoint(panel, 0, 0, getRootPane().getLayeredPane());
        return new Point(location.x, location.y + 5);
      };
      return panel;
    }

    private JComponent createActionLink(final String text, final String groupId, Icon icon, boolean focusListOnLeft) {
      final Ref<ActionLink> ref = new Ref<>(null);
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
      JComponent panel = createActionLink(text, icon, ref, action);
      installFocusable(panel, action, KeyEvent.VK_UP, KeyEvent.VK_DOWN, focusListOnLeft);
      return panel;
    }

    private JComponent createActionLink(String text, Icon icon, Ref<ActionLink> ref, AnAction action) {
      ActionLink link = new ActionLink(text, icon, action);
      ref.set(link);
      // Don't allow focus, as the containing panel is going to focusable.
      link.setFocusable(false);
      link.setPaintUnderline(false);
      link.setNormalColor(getLinkNormalColor());
      JActionLinkPanel panel = new JActionLinkPanel(link);
      panel.setBorder(JBUI.Borders.empty(4, 6, 4, 6));
      panel.add(createArrow(link), BorderLayout.EAST);
      return panel;
    }

    private JComponent createActionPanel() {
      JPanel actions = new NonOpaquePanel();
      actions.setBorder(JBUI.Borders.emptyLeft(10));
      actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
      ActionManager actionManager = ActionManager.getInstance();
      ActionGroup quickStart = (ActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
      DefaultActionGroup group = new DefaultActionGroup();
      collectAllActions(group, quickStart);

      for (AnAction action : group.getChildren(null)) {
        AnActionEvent e =
          AnActionEvent.createFromAnAction(action, null, ActionPlaces.WELCOME_SCREEN, DataManager.getInstance().getDataContext(this));
        action.update(e);
        Presentation presentation = e.getPresentation();
        if (presentation.isVisible()) {
          String text = presentation.getText();
          if (text != null && text.endsWith("...")) {
            text = text.substring(0, text.length() - 3);
          }
          Icon icon = presentation.getIcon();
          if (icon.getIconHeight() != JBUI.scale(16) || icon.getIconWidth() != JBUI.scale(16)) {
            icon = JBUI.emptyIcon(16);
          }
          action = wrapGroups(action);
          ActionLink link = new ActionLink(text, icon, action, createUsageTracker(action));
          // Don't allow focus, as the containing panel is going to focusable.
          link.setFocusable(false);
          link.setPaintUnderline(false);
          link.setNormalColor(getLinkNormalColor());
          JActionLinkPanel button = new JActionLinkPanel(link);
          button.setBorder(JBUI.Borders.empty(8, 20));
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

    /**
     * Wraps an {@link ActionLink} component and delegates accessibility support to it.
     */
    protected class JActionLinkPanel extends JPanel {
      @NotNull private ActionLink myActionLink;

      public JActionLinkPanel(@NotNull ActionLink actionLink) {
        super(new BorderLayout());
        myActionLink = actionLink;
        add(myActionLink);
        NonOpaquePanel.setTransparent(this);
      }

      @Override
      public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
          accessibleContext = new AccessibleJActionLinkPanel(myActionLink.getAccessibleContext());
        }
        return accessibleContext;
      }

      protected class AccessibleJActionLinkPanel extends AccessibleContextDelegate {
        public AccessibleJActionLinkPanel(AccessibleContext context) {
          super(context);
        }

        @Override
        public Accessible getAccessibleParent() {
          if (getParent() instanceof Accessible) {
            return (Accessible)getParent();
          }
          return super.getAccessibleParent();
        }

        @Override
        public AccessibleRole getAccessibleRole() {
          return AccessibleRole.PUSH_BUTTON;
        }
      }
    }

    private AnAction wrapGroups(AnAction action) {
      if (action instanceof ActionGroup && ((ActionGroup)action).isPopup()) {
        final Pair<JPanel, JBList> panel = createActionGroupPanel((ActionGroup)action, mySlidingPanel, () -> goBack());
        final Runnable onDone = () -> {
          setTitle("New Project");
          final JBList list = panel.second;
          ScrollingUtil.ensureSelectionExists(list);
          final ListSelectionListener[] listeners =
            ((DefaultListSelectionModel)list.getSelectionModel()).getListeners(ListSelectionListener.class);

          //avoid component cashing. This helps in case of LaF change
          for (ListSelectionListener listener : listeners) {
            listener.valueChanged(new ListSelectionEvent(list, list.getSelectedIndex(), list.getSelectedIndex(), true));
          }
          list.requestFocus();
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
      mySlidingPanel.swipe("root", JBCardLayout.SwipeDirection.BACKWARD).doWhenDone(() -> {
        mySlidingPanel.getRootPane().setDefaultButton(null);
        setTitle(getWelcomeFrameTitle());
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
      String appVersion = "Version ";

      appVersion += app.getFullVersion();

      if (app.isEAP() && !app.getBuild().isSnapshot()) {
        appVersion += " (" + app.getBuild().asStringWithoutProductCode() + ")";
      }

      JLabel version = new JLabel(appVersion);
      version.setFont(getProductFont().deriveFont(JBUI.scale(16f)));
      version.setHorizontalAlignment(SwingConstants.CENTER);
      version.setForeground(Gray._128);

      panel.add(appName);
      panel.add(version, BorderLayout.SOUTH);
      panel.setBorder(JBUI.Borders.emptyBottom(20));
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
      panel.setBorder(new CustomLineBorder(getSeparatorColor(), JBUI.insetsRight(1)));
      return panel;
    }

    private void installFocusable(final JComponent comp, final AnAction action, final int prevKeyCode, final int nextKeyCode, final boolean focusListOnLeft) {
      comp.setFocusable(true);
      comp.setFocusTraversalKeysEnabled(true);
      comp.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          final JList list = UIUtil.findComponentOfType(FlatWelcomeFrame.this.getComponent(), JList.class);
          if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
            InputEvent event = e;
            if (e.getComponent() instanceof JComponent) {
              ActionLink link = UIUtil.findComponentOfType((JComponent)e.getComponent(), ActionLink.class);
              if (link != null) {
                event = new MouseEvent(link, MouseEvent.MOUSE_CLICKED, e.getWhen(), e.getModifiers(), 0, 0, 1, false, MouseEvent.BUTTON1);
              }
            }
            action.actionPerformed(AnActionEvent.createFromAnAction(action, event, ActionPlaces.WELCOME_SCREEN, DataManager.getInstance().getDataContext()));
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
    return () -> UsageTrigger.trigger("welcome.screen." + ActionManager.getInstance().getId(action));
  }

  private static JLabel createArrow(final ActionLink link) {
    JLabel arrow = new JLabel(AllIcons.General.Combo3);
    arrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    arrow.setVerticalAlignment(SwingConstants.BOTTOM);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        final MouseEvent newEvent = MouseEventAdapter.convert(e, link, e.getX(), e.getY());
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

  public static Pair<JPanel, JBList> createActionGroupPanel(final ActionGroup action, final JComponent parent, final Runnable backAction) {
    JPanel actionsListPanel = new JPanel(new BorderLayout());
    actionsListPanel.setBackground(getProjectsBackground());
    final List<AnAction> groups = flattenActionGroups(action);
    final JBList list = new JBList(groups);

    list.setBackground(getProjectsBackground());
    list.setCellRenderer(new GroupedItemsListRenderer(new ListItemDescriptorAdapter<AnAction>() {
       @Nullable
       @Override
       public String getTextFor(AnAction value) {
         return getActionText(value);
       }

       @Nullable
       @Override
       public String getCaptionAboveOf(AnAction value) {
         return getParentGroupName(value);
       }

       @Override
       public boolean hasSeparatorAboveOf(AnAction value) {
         int index = groups.indexOf(value);
         final String parentGroupName = getParentGroupName(value);

         if (index < 1) return parentGroupName != null;
         AnAction upper = groups.get(index - 1);
         if (getParentGroupName(upper) == null && parentGroupName != null) return true;

         return !Comparing.equal(getParentGroupName(upper), parentGroupName);
       }
     })

     {
       @Override
       protected JComponent createItemComponent() {
         myTextLabel = new ErrorLabel();
         myTextLabel.setOpaque(true);
         myTextLabel.setBorder(JBUI.Borders.empty(3, 7));

         return myTextLabel;
       }

       @Override
       protected Color getBackground() {
         return getProjectsBackground();
       }

       @Override
       protected void customizeComponent(JList list, Object value, boolean isSelected) {
         if (myTextLabel != null) {
           myTextLabel.setText(getActionText(((AnAction)value)));
           myTextLabel.setIcon(((AnAction)value).getTemplatePresentation().getIcon());
         }
       }
     }
    );

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
          panel.setBorder(JBUI.Borders.empty(7, 10));
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
    installQuickSearch(list);
    return Pair.create(main, list);
  }

  public static void installQuickSearch(JBList list) {
    new ListSpeedSearch(list, new Convertor<Object, String>() {
      @Override
      public String convert(Object o) {
        if (o instanceof AbstractActionWithPanel) { //to avoid dependency mess with ProjectSettingsStepBase
          return ((AbstractActionWithPanel)o).getTemplatePresentation().getText();
        }
        return null;
      }
    });
  }

  private static List<AnAction> flattenActionGroups(@NotNull final ActionGroup action) {
    final ArrayList<AnAction> groups = ContainerUtil.newArrayList();
    String groupName;
    for (AnAction anAction : action.getChildren(null)) {
      if (anAction instanceof ActionGroup) {
        groupName = getActionText(anAction);
        for (AnAction childAction : ((ActionGroup)anAction).getChildren(null)) {
          if (groupName != null) {
            setParentGroupName(groupName, childAction);
          }
          groups.add(childAction);
        }
      } else {
        groups.add(anAction);
      }
    }
    return groups;
  }

  private static String getActionText(@NotNull final AnAction value) {
    return value.getTemplatePresentation().getText();
  }

  private static String getParentGroupName(@NotNull final AnAction value) {
    return (String)value.getTemplatePresentation().getClientProperty(ACTION_GROUP_KEY);
  }

  private static void setParentGroupName(@NotNull final String groupName, @NotNull final AnAction childAction) {
    childAction.getTemplatePresentation().putClientProperty(ACTION_GROUP_KEY, groupName);
  }
}
