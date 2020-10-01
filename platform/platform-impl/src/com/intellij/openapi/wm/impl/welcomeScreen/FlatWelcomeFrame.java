// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.ide.lightEdit.LightEditServiceListener;
import com.intellij.ide.plugins.PluginDropHandler;
import com.intellij.ide.plugins.newui.VerticalLayout;
import com.intellij.idea.SplashManager;
import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.JBProtocolCommand;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WindowStateService;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomFrameDialogContent;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.DefaultFrameHeader;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.FrameHeader;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.mac.TouchbarDataKeys;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IconUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextAccessor;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;

import static com.intellij.openapi.actionSystem.IdeActions.GROUP_FILE;
import static com.intellij.openapi.actionSystem.IdeActions.GROUP_HELP_MENU;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenActionsUtil.collectAllActions;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.*;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenFocusManager.installFocusable;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getLinkNormalColor;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getMainBackground;
import static com.intellij.util.ui.update.UiNotifyConnector.doWhenFirstShown;

/**
 * @author Konstantin Bulenkov
 */
public class FlatWelcomeFrame extends JFrame implements IdeFrame, Disposable, AccessibleContextAccessor, WelcomeFrameUpdater {
  public static final String BOTTOM_PANEL = "BOTTOM_PANEL";
  public static final int DEFAULT_HEIGHT = Registry.is("use.tabbed.welcome.screen") ? 600 : 460;
  public static final int MAX_DEFAULT_WIDTH = 800;
  private final AbstractWelcomeScreen myScreen;
  private WelcomeBalloonLayoutImpl myBalloonLayout;
  private boolean myDisposed;

  public FlatWelcomeFrame() {
    SplashManager.hideBeforeShow(this);

    JRootPane rootPane = getRootPane();
    boolean useTabWelcomeScreen = Registry.is("use.tabbed.welcome.screen");
    myBalloonLayout = new WelcomeBalloonLayoutImpl(rootPane, JBUI.insets(8));
    myScreen = useTabWelcomeScreen ? new TabbedWelcomeScreen() : new FlatWelcomeScreen();

    IdeGlassPaneImpl glassPane = new IdeGlassPaneImpl(rootPane) {
      @Override
      public void addNotify() {
        super.addNotify();
        ApplicationManager.getApplication().invokeLater(() -> JBProtocolCommand.handleCurrentCommand());
      }
    };

    setGlassPane(glassPane);
    glassPane.setVisible(false);

    int defaultHeight = DEFAULT_HEIGHT;

    if (IdeFrameDecorator.isCustomDecorationActive()) {
      Color backgroundColor = UIManager.getColor("WelcomeScreen.background");

      FrameHeader header = new DefaultFrameHeader(this);

      if (backgroundColor != null) {
        header.setBackground(backgroundColor);
      }

      JComponent holder = CustomFrameDialogContent
        .getCustomContentHolder(this, myScreen.getWelcomePanel(), header);

      setContentPane(holder);
      if (holder instanceof CustomFrameDialogContent) {
        defaultHeight += ((CustomFrameDialogContent)holder).getHeaderHeight();
      }
    }
    else {
      if (useTabWelcomeScreen && SystemInfo.isMac) {
        rootPane.setJMenuBar(new WelcomeFrameMenuBar());
      }
      setContentPane(myScreen.getWelcomePanel());
    }

    setTitle(getWelcomeFrameTitle());
    AppUIUtil.updateWindowIcon(this);
    if (useTabWelcomeScreen) {
      getRootPane().setPreferredSize(JBUI.size(MAX_DEFAULT_WIDTH, defaultHeight));
    }
    else {
      int width = RecentProjectListActionProvider.getInstance().getActions(false).size() == 0 ? 666 : MAX_DEFAULT_WIDTH;
      getRootPane().setPreferredSize(JBUI.size(width, defaultHeight));
    }
    setResizable(useTabWelcomeScreen);

    Dimension size = getPreferredSize();
    Point location = WindowStateService.getInstance().getLocation(WelcomeFrame.DIMENSION_KEY);
    Rectangle screenBounds = ScreenUtil.getScreenRectangle(location != null ? location : new Point(0, 0));
    setBounds(
      screenBounds.x + (screenBounds.width - size.width) / 2,
      screenBounds.y + (screenBounds.height - size.height) / 3,
      size.width,
      size.height
    );

    setAutoRequestFocus(false);

    // at this point a window insets may be unavailable,
    // so we need resize window when it is shown
    doWhenFirstShown(this, this::pack);

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        Disposer.dispose(FlatWelcomeFrame.this);
      }
    });
    connection.subscribe(LightEditService.TOPIC, new LightEditServiceListener() {
      @Override
      public void lightEditWindowOpened(@NotNull Project project) {
        Disposer.dispose(FlatWelcomeFrame.this);
      }
    });
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appClosing() {
        saveLocation(getBounds());
      }
    });

    WelcomeFrame.setupCloseAction(this);
    MnemonicHelper.init(this);
    Disposer.register(ApplicationManager.getApplication(), this);

    UIUtil.decorateWindowHeader(getRootPane());
    UIUtil.setCustomTitleBar(this, getRootPane(), runnable -> Disposer.register(this, () -> runnable.run()));
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

  private static void saveLocation(@NotNull Rectangle location) {
    Point middle = new Point(location.x + location.width / 2, location.y + location.height / 2);
    WindowStateService.getInstance().putLocation(WelcomeFrame.DIMENSION_KEY, middle);
  }

  @Nullable
  @Override
  public StatusBar getStatusBar() {
    return null;
  }

  @Override
  public AccessibleContext getCurrentAccessibleContext() {
    return accessibleContext;
  }

  protected String getWelcomeFrameTitle() {
    return getApplicationTitle();
  }

  @NotNull
  public static JComponent getPreferredFocusedComponent(@NotNull Pair<JPanel, JBList<AnAction>> pair) {
    if (pair.second.getModel().getSize() == 1) {
      JBTextField textField = UIUtil.uiTraverser(pair.first).filter(JBTextField.class).first();
      if (textField != null) {
        return textField;
      }
    }
    return pair.second;
  }

  private final class FlatWelcomeScreen extends AbstractWelcomeScreen implements WelcomeFrameUpdater {
    private final DefaultActionGroup myTouchbarActions = new DefaultActionGroup();
    private LinkLabel<Object> myUpdatePluginsLink;
    private boolean inDnd;

    FlatWelcomeScreen() {
      setBackground(getMainBackground());
      if (RecentProjectListActionProvider.getInstance().getActions(false, true).size() > 0) {
        JComponent recentProjects = createRecentProjects(this);
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

      TouchbarDataKeys.putActionDescriptor(myTouchbarActions).setShowText(true);
    }

    @SuppressWarnings("UseJBColor")
    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (inDnd) {
        Rectangle bounds = getBounds();
        g.setColor(JBColor.namedColor("DragAndDrop.areaBackground", 0x3d7dcc, 0x404a57));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        Color backgroundBorder = JBColor.namedColor("DragAndDrop.areaBorderColor", new Color(137, 178, 222));
        g.setColor(backgroundBorder);
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2);

        Color foreground = JBColor.namedColor("DragAndDrop.areaForeground", Gray._120);
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

    @NotNull
    private JComponent createBody() {
      NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
      panel.add(createLogo(), BorderLayout.NORTH);
      myTouchbarActions.removeAll();
      ActionPanel actionPanel = createQuickStartActionPanel();
      panel.add(actionPanel, BorderLayout.CENTER);
      myTouchbarActions.addAll(actionPanel.getActions());
      panel.add(createUpdatesSettingsAndDocs(), BorderLayout.SOUTH);
      return panel;
    }

    private JComponent createUpdatesSettingsAndDocs() {
      JPanel panel = new NonOpaquePanel(new BorderLayout());
      panel.add(createUpdatePluginsLink(), BorderLayout.WEST);
      panel.add(createSettingsAndDocsPanel(FlatWelcomeFrame.this), BorderLayout.EAST);
      return panel;
    }

    private JComponent createSettingsAndDocsPanel(JFrame frame) {
      JPanel panel = new NonOpaquePanel(new BorderLayout());
      NonOpaquePanel toolbar = new NonOpaquePanel();

      toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
      toolbar.add(createErrorsLink(this));
      toolbar.add(createEventsLink());
      toolbar.add(createActionLink(FlatWelcomeFrame.this, IdeBundle.message("action.Anonymous.text.configure"),
                                   IdeActions.GROUP_WELCOME_SCREEN_CONFIGURE,
                                   AllIcons.General.GearPlain, UIUtil.findComponentOfType(frame.getRootPane(), JList.class)));
      toolbar
        .add(createActionLink(FlatWelcomeFrame.this, IdeBundle.message("action.GetHelp"), IdeActions.GROUP_WELCOME_SCREEN_DOC, null, null
        ));
      panel.add(toolbar, BorderLayout.EAST);

      panel.setBorder(JBUI.Borders.empty(0, 0, 8, 11));
      return panel;
    }

    private Component createEventsLink() {
      return createEventLink(IdeBundle.message("action.Events"), FlatWelcomeFrame.this);
    }

    @NotNull
    private ActionPanel createQuickStartActionPanel() {
      DefaultActionGroup group = new DefaultActionGroup();
      ActionGroup quickStart = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
      collectAllActions(group, quickStart);

      ActionPanel mainPanel =
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
          String text = presentation.getText();
          if (text != null && text.endsWith("...")) {
            text = text.substring(0, text.length() - 3);
          }
          Icon icon = presentation.getIcon();
          if (icon == null || icon.getIconHeight() != JBUIScale.scale(16) || icon.getIconWidth() != JBUIScale.scale(16)) {
            icon = icon != null ? IconUtil.scale(icon, null, 16f / icon.getIconWidth()) : JBUIScale.scaleIcon(EmptyIcon.create(16));
            icon = IconUtil.colorize(icon, new JBColor(0x6e6e6e, 0xafb1b3));
          }
          action = ActionGroupPanelWrapper.wrapGroups(action, this);
          ActionLink link = new ActionLink(text, icon, action, null, ActionPlaces.WELCOME_SCREEN);
          // Don't allow focus, as the containing panel is going to focusable.
          link.setFocusable(false);
          link.setPaintUnderline(false);
          link.setNormalColor(getLinkNormalColor());
          JActionLinkPanel button = new JActionLinkPanel(link);
          button.setBorder(JBUI.Borders.empty(8, 20));
          if (action instanceof WelcomePopupAction) {
            button.add(createArrow(link), BorderLayout.EAST);
            TouchbarDataKeys.putActionDescriptor(action).setContextComponent(link);
          }
          installFocusable(FlatWelcomeFrame.this, button, action, KeyEvent.VK_DOWN,
                           KeyEvent.VK_UP, UIUtil.findComponentOfType(FlatWelcomeFrame.this.getComponent(), JList.class)
          );

          panel.add(button);
          mainPanel.addAction(action);
        }
      }

      return mainPanel;
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      if (TouchbarDataKeys.ACTIONS_KEY.is(dataId)) {
        return myTouchbarActions;
      }
      return null;
    }

    private JComponent createUpdatePluginsLink() {
      myUpdatePluginsLink = new LinkLabel<>(IdeBundle.message("updates.plugins.welcome.screen.link.message"), null);
      myUpdatePluginsLink.setVisible(false);

      NonOpaquePanel wrap = new NonOpaquePanel(myUpdatePluginsLink);
      wrap.setBorder(JBUI.Borders.empty(0, 10, 8, 11));
      return wrap;
    }

    @Override
    public void showPluginUpdates(@NotNull Runnable callback) {
      myUpdatePluginsLink.setListener((__, ___) -> callback.run(), null);
      myUpdatePluginsLink.setVisible(true);
    }

    @Override
    public void hidePluginUpdates() {
      myUpdatePluginsLink.setListener(null, null);
      myUpdatePluginsLink.setVisible(false);
    }
  }

  protected void extendActionsGroup(JPanel panel) {
  }

  protected void onFirstActionShown(@NotNull Component action) {
  }

  @Override
  public void showPluginUpdates(@NotNull Runnable callback) {
    if (myScreen instanceof WelcomeFrameUpdater) {
      ((WelcomeFrameUpdater)myScreen).showPluginUpdates(callback);
    }
  }

  @Override
  public void hidePluginUpdates() {
    if (myScreen instanceof WelcomeFrameUpdater) {
      ((WelcomeFrameUpdater)myScreen).hidePluginUpdates();
    }
  }

  @Nullable
  @Override
  public BalloonLayout getBalloonLayout() {
    return myBalloonLayout;
  }

  @NotNull
  @Override
  public Rectangle suggestChildFrameBounds() {
    return getBounds();
  }

  @Nullable
  @Override
  public Project getProject() {
    if (ApplicationManager.getApplication().isDisposed()) {
      return null;
    }
    return ProjectManager.getInstance().getDefaultProject();
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
      return new DefaultActionGroup(ActionManager.getInstance().getAction(GROUP_FILE),
                                    ActionManager.getInstance().getAction(GROUP_HELP_MENU));
    }
  }
}
