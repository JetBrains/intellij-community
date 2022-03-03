// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.lightEdit.LightEditServiceListener;
import com.intellij.ide.plugins.PluginDropHandler;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.idea.SplashManager;
import com.intellij.jdkEx.JdkEx;
import com.intellij.notification.NotificationsManager;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.WindowStateService;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomFrameDialogContent;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.DefaultFrameHeader;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.mac.touchbar.Touchbar;
import com.intellij.ui.mac.touchbar.TouchbarActionCustomizations;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IconUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextAccessor;
import com.intellij.util.ui.update.UiNotifyConnector;
import net.miginfocom.swing.MigLayout;
import org.jdom.internal.SystemProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.List;

import static com.intellij.util.ObjectUtils.chooseNotNull;

/**
 * @author Konstantin Bulenkov
 */
public class FlatWelcomeFrame extends JFrame implements IdeFrame, Disposable, AccessibleContextAccessor {
  @SuppressWarnings("StaticNonFinalField")
  public static boolean USE_TABBED_WELCOME_SCREEN = Boolean.parseBoolean(SystemProperty.get("use.tabbed.welcome.screen", "true"));

  public static final String BOTTOM_PANEL = "BOTTOM_PANEL";
  public static final int DEFAULT_HEIGHT = USE_TABBED_WELCOME_SCREEN ? 600 : 460;
  public static final int MAX_DEFAULT_WIDTH = 800;
  private final AbstractWelcomeScreen myScreen;
  private WelcomeBalloonLayoutImpl myBalloonLayout;
  private boolean myDisposed;
  private DefaultFrameHeader myHeader;

  public FlatWelcomeFrame() {
    SplashManager.hideBeforeShow(this);

    JRootPane rootPane = getRootPane();
    myBalloonLayout = new WelcomeBalloonLayoutImpl(rootPane, JBUI.insets(8));
    myScreen = USE_TABBED_WELCOME_SCREEN ? new TabbedWelcomeScreen() : new FlatWelcomeScreen();

    if (IdeFrameDecorator.isCustomDecorationActive()) {
      myHeader = new DefaultFrameHeader(this);
      setContentPane(CustomFrameDialogContent.getCustomContentHolder(this, myScreen.getWelcomePanel(), myHeader));
    }
    else {
      if (USE_TABBED_WELCOME_SCREEN && SystemInfoRt.isMac) {
        rootPane.setJMenuBar(new WelcomeFrameMenuBar().setFrame(this));
      }
      setContentPane(myScreen.getWelcomePanel());
    }

    IdeGlassPaneImpl glassPane = new IdeGlassPaneImpl(rootPane);
    setGlassPane(glassPane);
    glassPane.setVisible(false);

    updateComponentsAndResize();

    // at this point, window insets may be unavailable, so we need to resize the window when it is shown
    UiNotifyConnector.doWhenFirstShown(this, this::pack);

    Application app = ApplicationManager.getApplication();
    MessageBusConnection connection = app.getMessageBus().connect(this);
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        Disposer.dispose(FlatWelcomeFrame.this);
      }
    });
    connection.subscribe(LightEditServiceListener.TOPIC, new LightEditServiceListener() {
      @Override
      public void lightEditWindowOpened(@NotNull Project project) {
        Disposer.dispose(FlatWelcomeFrame.this);
      }
    });
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appClosing() {
        saveSizeAndLocation(getBounds());
      }
    });
    connection.subscribe(LafManagerListener.TOPIC, new LafManagerListener() {
      @Override
      public void lookAndFeelChanged(@NotNull LafManager source) {
        if (myBalloonLayout != null) {
          Disposer.dispose(myBalloonLayout);
        }
        myBalloonLayout = new WelcomeBalloonLayoutImpl(rootPane, JBUI.insets(8));
        updateComponentsAndResize();
        repaint();
      }
    });

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent componentEvent) {
        if (WindowStateService.getInstance().getSize(WelcomeFrame.DIMENSION_KEY) != null) {
          saveSizeAndLocation(getBounds());
        }
      }
    });

    setupCloseAction();
    MnemonicHelper.init(this);
    Disposer.register(app, this);

    UIUtil.decorateWindowHeader(getRootPane());
    ToolbarUtil.setTransparentTitleBar(this, getRootPane(), runnable -> Disposer.register(this, () -> runnable.run()));

    app.invokeLater(
      () -> ((NotificationsManagerImpl)NotificationsManager.getNotificationsManager()).dispatchEarlyNotifications(),
      ModalityState.NON_MODAL);
  }

  protected void setupCloseAction() {
    WelcomeFrame.setupCloseAction(this);
  }

  private void updateComponentsAndResize() {
    int defaultHeight = DEFAULT_HEIGHT;
    if (IdeFrameDecorator.isCustomDecorationActive()) {
      Color backgroundColor = UIManager.getColor("WelcomeScreen.background");
      if (backgroundColor != null) {
        myHeader.setBackground(backgroundColor);
      }
    }
    else {
      if (USE_TABBED_WELCOME_SCREEN && SystemInfoRt.isMac) {
        rootPane.setJMenuBar(new WelcomeFrameMenuBar().setFrame(this));
      }
      setContentPane(myScreen.getWelcomePanel());
    }
    if (USE_TABBED_WELCOME_SCREEN) {
      JBDimension defaultSize = JBUI.size(MAX_DEFAULT_WIDTH, defaultHeight);
      setPreferredSize(chooseNotNull(WindowStateService.getInstance().getSize(WelcomeFrame.DIMENSION_KEY), defaultSize));
      setMinimumSize(defaultSize);
    }
    else {
      int width = RecentProjectListActionProvider.getInstance().getActions(false).size() == 0 ? 666 : MAX_DEFAULT_WIDTH;
      setPreferredSize(JBUI.size(width, defaultHeight));
    }
    setResizable(USE_TABBED_WELCOME_SCREEN);

    Dimension size = getPreferredSize();
    Point location = WindowStateService.getInstance().getLocation(WelcomeFrame.DIMENSION_KEY);
    Rectangle screenBounds = ScreenUtil.getScreenRectangle(location != null ? location : new Point(0, 0));
    setBounds(
      screenBounds.x + (screenBounds.width - size.width) / 2,
      screenBounds.y + (screenBounds.height - size.height) / 3,
      size.width,
      size.height
    );
    UIUtil.decorateWindowHeader(getRootPane());
    setTitle("");
    setTitle(getWelcomeFrameTitle());
    AppUIUtil.updateWindowIcon(this);
  }

  @Override
  public void addNotify() {
    if (IdeFrameDecorator.isCustomDecorationActive()) {
      JdkEx.setHasCustomDecoration(this);
    }
    super.addNotify();
  }

  @Override
  public void dispose() {
    if (myDisposed) {
      return;
    }
    myDisposed = true;
    super.dispose();
    if (myBalloonLayout != null) {
      Disposer.dispose((myBalloonLayout));
      myBalloonLayout = null;
    }
    Disposer.dispose(myScreen);
    WelcomeFrame.resetInstance();
  }

  private static void saveSizeAndLocation(@NotNull Rectangle location) {
    Point middle = new Point(location.x + location.width / 2, location.y + location.height / 2);
    WindowStateService.getInstance().putLocation(WelcomeFrame.DIMENSION_KEY, middle);
    WindowStateService.getInstance().putSize(WelcomeFrame.DIMENSION_KEY, location.getSize());
  }

  @Override
  public @Nullable StatusBar getStatusBar() {
    return null;
  }

  @Override
  public AccessibleContext getCurrentAccessibleContext() {
    return accessibleContext;
  }

  protected String getWelcomeFrameTitle() {
    return WelcomeScreenComponentFactory.getApplicationTitle();
  }

  public static @NotNull JComponent getPreferredFocusedComponent(@NotNull Pair<JPanel, JBList<AnAction>> pair) {
    if (pair.second.getModel().getSize() == 1) {
      JBTextField textField = UIUtil.uiTraverser(pair.first).filter(JBTextField.class).first();
      if (textField != null) {
        return textField;
      }
    }
    return pair.second;
  }

  private final class FlatWelcomeScreen extends AbstractWelcomeScreen {
    private final DefaultActionGroup myTouchbarActions = new DefaultActionGroup();
    private boolean inDnd;

    FlatWelcomeScreen() {
      setBackground(WelcomeScreenUIManager.getMainBackground());
      if (RecentProjectListActionProvider.getInstance().getActions(false, true).size() > 0) {
        JComponent recentProjects = WelcomeScreenComponentFactory.createRecentProjects(this);
        add(recentProjects, BorderLayout.WEST);
        JList<?> projectsList = UIUtil.findComponentOfType(recentProjects, JList.class);
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
              if (RecentProjectListActionProvider.getInstance().getActions(false, true).size() == 0) {
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
      setDropTarget(new DropTarget(this, new DropTargetAdapter() {
        @Override
        public void dragEnter(DropTargetDragEvent e) {
          setDnd(true);
        }

        @Override
        public void dragExit(DropTargetEvent e) {
          setDnd(false);
        }

        @Override
        public void drop(DropTargetDropEvent e) {
          setDnd(false);
          e.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
          Transferable transferable = e.getTransferable();
          List<Path> list = FileCopyPasteUtil.getFiles(transferable);
          if (list != null && list.size() > 0) {
            PluginDropHandler pluginHandler = new PluginDropHandler();
            if (!pluginHandler.canHandle(transferable, null) || !pluginHandler.handleDrop(transferable, null, null)) {
              ProjectUtil.tryOpenFiles(null, list, "WelcomeFrame");
            }
            e.dropComplete(true);
            return;
          }
          e.dropComplete(false);
        }

        private void setDnd(boolean dnd) {
          inDnd = dnd;
          repaint();
        }
      }));

      TouchbarActionCustomizations.setShowText(myTouchbarActions, true);
      Touchbar.setActions(this, myTouchbarActions);
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (inDnd) {
        Rectangle bounds = getBounds();
        g.setColor(JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        Color backgroundBorder = JBUI.CurrentTheme.DragAndDrop.BORDER_COLOR;
        g.setColor(backgroundBorder);
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2);

        Color foreground = JBUI.CurrentTheme.DragAndDrop.Area.FOREGROUND;
        g.setColor(foreground);
        Font labelFont = StartupUiUtil.getLabelFont();
        Font font = labelFont.deriveFont(labelFont.getSize() + 5.0f);
        String drop = IdeBundle.message("welcome.screen.drop.files.to.open.text");
        g.setFont(font);
        int dropWidth = g.getFontMetrics().stringWidth(drop);
        int dropHeight = g.getFontMetrics().getHeight();
        g.drawString(drop, bounds.x + (bounds.width - dropWidth) / 2, (int)(bounds.y + (bounds.height - dropHeight) * 0.45));
      }
    }

    private @NotNull JComponent createBody() {
      NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
      panel.add(WelcomeScreenComponentFactory.createLogo(), BorderLayout.NORTH);
      myTouchbarActions.removeAll();
      ActionPanel actionPanel = createQuickStartActionPanel();
      panel.add(actionPanel, BorderLayout.CENTER);
      myTouchbarActions.addAll(actionPanel.getActions());
      panel.add(createSettingsAndDocsPanel(FlatWelcomeFrame.this), BorderLayout.SOUTH);
      return panel;
    }

    private JComponent createSettingsAndDocsPanel(JFrame frame) {
      JPanel panel = new NonOpaquePanel(new BorderLayout());
      NonOpaquePanel toolbar = new NonOpaquePanel();

      toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
      toolbar.add(WelcomeScreenComponentFactory.createErrorsLink(this));
      toolbar.add(createEventsLink());
      toolbar.add(WelcomeScreenComponentFactory.createActionLink(
        FlatWelcomeFrame.this, IdeBundle.message("action.Anonymous.text.configure"), IdeActions.GROUP_WELCOME_SCREEN_CONFIGURE, AllIcons.General.GearPlain,
        UIUtil.findComponentOfType(frame.getRootPane(), JList.class)));
      toolbar.add(WelcomeScreenComponentFactory.createActionLink(
        FlatWelcomeFrame.this, IdeBundle.message("action.GetHelp"), IdeActions.GROUP_WELCOME_SCREEN_DOC, null, null));
      panel.add(toolbar, BorderLayout.EAST);

      panel.setBorder(JBUI.Borders.empty(0, 0, 8, 11));
      return panel;
    }

    private Component createEventsLink() {
      return WelcomeScreenComponentFactory.createEventLink(IdeBundle.message("action.Events"), FlatWelcomeFrame.this);
    }

    private @NotNull ActionPanel createQuickStartActionPanel() {
      DefaultActionGroup group = new DefaultActionGroup();
      ActionGroup quickStart = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
      WelcomeScreenActionsUtil.collectAllActions(group, quickStart);

      @SuppressWarnings("SpellCheckingInspection") ActionPanel mainPanel =
        new ActionPanel(new MigLayout("ins 0, novisualpadding, gap " + JBUI.scale(5) + ", flowy", "push[pref!, center]push"));
      mainPanel.setOpaque(false);

      JPanel panel = new JPanel(new VerticalLayout(JBUI.scale(5))) {
        Component firstAction = null;

        @Override
        public Component add(Component comp) {
          Component cmp = super.add(comp);
          if (firstAction == null) {
            firstAction = cmp;
          }
          return cmp;
        }

        @Override
        public void addNotify() {
          super.addNotify();
          if (firstAction != null) {
            onFirstActionShown(firstAction);
          }
        }
      };
      panel.setOpaque(false);

      extendActionsGroup(mainPanel);
      mainPanel.add(panel);

      for (AnAction action : group.getChildren(null)) {
        AnActionEvent e =
          AnActionEvent.createFromAnAction(action, null, ActionPlaces.WELCOME_SCREEN, DataManager.getInstance().getDataContext(this));
        action.update(e);
        Presentation presentation = e.getPresentation();
        if (presentation.isVisible()) {
          @SuppressWarnings("DialogTitleCapitalization") String text = presentation.getText();
          if (text != null && text.endsWith("...")) {
            text = text.substring(0, text.length() - 3);
          }
          Icon icon = presentation.getIcon();
          if (icon == null || icon.getIconHeight() != JBUIScale.scale(16) || icon.getIconWidth() != JBUIScale.scale(16)) {
            icon = icon != null ? IconUtil.scale(icon, null, 16f / icon.getIconWidth()) : JBUIScale.scaleIcon(EmptyIcon.create(16));
            icon = IconUtil.colorize(icon, new JBColor(0x6e6e6e, 0xafb1b3));
          }
          action = ActionGroupPanelWrapper.wrapGroups(action, this);
          @SuppressWarnings("deprecation") ActionLink link = new ActionLink(text, icon, action, null, ActionPlaces.WELCOME_SCREEN);
          link.setFocusable(false);  // don't allow focus, as the containing panel is going to be focusable
          link.setPaintUnderline(false);
          link.setNormalColor(WelcomeScreenUIManager.getLinkNormalColor());
          WelcomeScreenComponentFactory.JActionLinkPanel button = new WelcomeScreenComponentFactory.JActionLinkPanel(link);
          button.setBorder(JBUI.Borders.empty(8, 20));
          if (action instanceof WelcomePopupAction) {
            button.add(WelcomeScreenComponentFactory.createArrow(link), BorderLayout.EAST);
            TouchbarActionCustomizations.setComponent(action, link);
          }
          WelcomeScreenFocusManager.installFocusable(FlatWelcomeFrame.this, button, action, KeyEvent.VK_DOWN, KeyEvent.VK_UP,
                                                     UIUtil.findComponentOfType(FlatWelcomeFrame.this.getComponent(), JList.class)
          );

          panel.add(button);
          mainPanel.addAction(action);
        }
      }

      return mainPanel;
    }
  }

  @SuppressWarnings({"unused", "IdentifierGrammar"})
  protected void extendActionsGroup(JPanel panel) { }

  @SuppressWarnings("unused")
  protected void onFirstActionShown(@NotNull Component action) { }

  @Override
  public @Nullable BalloonLayout getBalloonLayout() {
    return myBalloonLayout;
  }

  @Override
  public @NotNull Rectangle suggestChildFrameBounds() {
    return getBounds();
  }

  @Override
  public @Nullable Project getProject() {
    return ApplicationManager.getApplication().isDisposed() ? null : ProjectManager.getInstance().getDefaultProject();
  }

  @Override
  public void setFrameTitle(String title) {
    setTitle(title);
  }

  @Override
  public JComponent getComponent() {
    return getRootPane();
  }

  private static class WelcomeFrameMenuBar extends IdeMenuBar {
    @Override
    public @NotNull ActionGroup getMainMenuActionGroup() {
      ActionManager manager = ActionManager.getInstance();
      return new DefaultActionGroup(manager.getAction(IdeActions.GROUP_FILE), manager.getAction(IdeActions.GROUP_HELP_MENU));
    }
  }
}
