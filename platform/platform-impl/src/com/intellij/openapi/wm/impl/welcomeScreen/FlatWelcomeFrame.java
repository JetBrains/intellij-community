// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.PluginDropHandler;
import com.intellij.ide.plugins.newui.VerticalLayout;
import com.intellij.idea.SplashManager;
import com.intellij.jdkEx.JdkEx;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.widget.IdeNotificationArea;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.JBProtocolCommand;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.ui.popup.StackingPopupDispatcher;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomFrameDialogContent;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBSlidingPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.mac.TouchbarDataKeys;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.MathUtil;
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
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.intellij.openapi.actionSystem.IdeActions.GROUP_FILE;
import static com.intellij.openapi.actionSystem.IdeActions.GROUP_HELP_MENU;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.*;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenFocusManager.installFocusable;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.*;
import static com.intellij.util.ui.update.UiNotifyConnector.doWhenFirstShown;

/**
 * @author Konstantin Bulenkov
 */
public class FlatWelcomeFrame extends JFrame implements IdeFrame, Disposable, AccessibleContextAccessor, WelcomeFrameUpdater {
  public static final String BOTTOM_PANEL = "BOTTOM_PANEL";
  private static final String ACTION_GROUP_KEY = "ACTION_GROUP_KEY";
  public static final int DEFAULT_HEIGHT = Registry.is("use.tabbed.welcome.screen") ? 600 : 460 ;
  public static final int MAX_DEFAULT_WIDTH = 800;
  private final AbstractWelcomeScreen myScreen;
  private boolean myDisposed;

  public FlatWelcomeFrame() {
    SplashManager.hideBeforeShow(this);

    JRootPane rootPane = getRootPane();
    boolean useTabWelcomeScreen = Registry.is("use.tabbed.welcome.screen");
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
      JComponent holder =
        CustomFrameDialogContent.getCustomContentHolder(this, myScreen.getWelcomePanel(), UIManager.getColor("WelcomeScreen.background"));
      setContentPane(holder);

      if(holder instanceof CustomFrameDialogContent)
      defaultHeight+= ((CustomFrameDialogContent)holder).getHeaderHeight();
    }
    else {
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
    if (Registry.is("use.tabbed.welcome.screen")) {
      rootPane.setJMenuBar(new WelcomeFrameMenuBar());
    }
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
    private final JBSlidingPanel mySlidingPanel = new JBSlidingPanel();
    private final DefaultActionGroup myTouchbarActions = new DefaultActionGroup();
    public Consumer<List<NotificationType>> myEventListener;
    public Computable<Point> myEventLocation;
    private LinkLabel<Object> myUpdatePluginsLink;
    private boolean inDnd;
    private BalloonLayoutImpl myBalloonLayout;

    FlatWelcomeScreen() {
      mySlidingPanel.add("root", this);
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
          List<File> list = FileCopyPasteUtil.getFileList(transferable);
          if (list != null && list.size() > 0) {
            PluginDropHandler pluginHandler = new PluginDropHandler();
            if (!pluginHandler.canHandle(transferable, null) || !pluginHandler.handleDrop(transferable, null, null)) {
              ProjectUtil.tryOpenFileList(null, list, "WelcomeFrame");
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

    @Override
    public JComponent getWelcomePanel() {
      return mySlidingPanel;
    }

    @SuppressWarnings("UseJBColor")
    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (inDnd) {
        Rectangle bounds = getBounds();
        Color background = JBColor.namedColor("DragAndDrop.areaBackground", new Color(225, 235, 245));
        g.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(), 206));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        Color backgroundBorder = JBColor.namedColor("DragAndDrop.areaBorderColor", new Color(137, 178, 222));
        g.setColor(backgroundBorder);
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.drawRect(bounds.x + 1 , bounds.y + 1, bounds.width - 2, bounds.height - 2);

        Color foreground = JBColor.namedColor("DragAndDrop.areaForeground", Gray._120);
        g.setColor(foreground);
        Font labelFont = StartupUiUtil.getLabelFont();
        Font font = labelFont.deriveFont(labelFont.getSize() + 5.0f);
        String drop = "Drop files here to open";
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
      toolbar.add(createActionLink(FlatWelcomeFrame.this, IdeBundle.message("action.Anonymous.text.configure"), IdeActions.GROUP_WELCOME_SCREEN_CONFIGURE,
                                   AllIcons.General.GearPlain, UIUtil.findComponentOfType(frame.getRootPane(), JList.class)));
      toolbar.add(createActionLink(FlatWelcomeFrame.this, IdeBundle.message("action.GetHelp"), IdeActions.GROUP_WELCOME_SCREEN_DOC, null, null
      ));
      panel.add(toolbar, BorderLayout.EAST);

      panel.setBorder(JBUI.Borders.empty(0, 0, 8, 11));
      return panel;
    }

    private JComponent createEventsLink() {
      final Ref<ActionLink> actionLinkRef = new Ref<>();
      final JComponent panel =
        createActionLink(IdeBundle.message("action.Events"), AllIcons.Ide.Notification.NoEvents, actionLinkRef, new AnAction() {
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
          actionLinkRef.get().setIcon(IdeNotificationArea.createIconWithNotificationCount(actionLinkRef.get(), type, types.size(), false));
          panel.setVisible(true);
        }
      };
      myEventLocation = () -> {
        Point location = SwingUtilities.convertPoint(panel, 0, 0, getRootPane().getLayeredPane());
        return new Point(location.x, location.y + 5);
      };
      myBalloonLayout = new WelcomeBalloonLayoutImpl(rootPane, JBUI.insets(8), myEventListener, myEventLocation);
      return panel;
    }

    @Override
    public @Nullable BalloonLayout getBalloonLayout() {
      return myBalloonLayout;
    }

    @NotNull
    private ActionPanel createQuickStartActionPanel() {
      DefaultActionGroup group = new DefaultActionGroup();
      ActionGroup quickStart = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
      collectAllActions(group, quickStart);

      ActionPanel mainPanel = new ActionPanel(new MigLayout("ins 0, novisualpadding, gap " + JBUI.scale(5) + ", flowy", "push[pref!, center]push"));
      mainPanel.setOpaque(false);

      JPanel panel = new JPanel(new VerticalLayout(JBUI.scale(5))) {
        Component firstAction = null;

        @Override
        public Component add(Component comp) {
          Component cmp = super.add(comp);
          if(firstAction == null) {
            firstAction = cmp;
          }
          return cmp;
        }

        @Override
        public void addNotify() {
          super.addNotify();

          if(firstAction != null) {
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
            icon = icon != null ? IconUtil.scale(icon, null, 16f / icon.getIconWidth()) : JBUI.scale(EmptyIcon.create(16));
            icon = IconUtil.colorize(icon, new JBColor(0x6e6e6e, 0xafb1b3));
          }
          action = wrapGroups(action);
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

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (TouchbarDataKeys.ACTIONS_KEY.is(dataId))
        return myTouchbarActions;
      return null;
    }

    private AnAction wrapGroups(AnAction action) {
      if (action instanceof ActionGroup && ((ActionGroup)action).isPopup()) {
        final Pair<JPanel, JBList<AnAction>> panel = createActionGroupPanel((ActionGroup)action, () -> goBack(), this);
        final Runnable onDone = () -> {
          setTitle(ProjectBundle.message("dialog.title.new.project"));
          final JBList<AnAction> list = panel.second;
          ScrollingUtil.ensureSelectionExists(list);
          final ListSelectionListener[] listeners =
            ((DefaultListSelectionModel)list.getSelectionModel()).getListeners(ListSelectionListener.class);

          //avoid component cashing. This helps in case of LaF change
          for (ListSelectionListener listener : listeners) {
            listener.valueChanged(new ListSelectionEvent(list, list.getSelectedIndex(), list.getSelectedIndex(), false));
          }
          JComponent toFocus = getPreferredFocusedComponent(panel);
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(toFocus, true));
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

    private void goBack() {
      mySlidingPanel.swipe("root", JBCardLayout.SwipeDirection.BACKWARD).doWhenDone(() -> {
        mySlidingPanel.getRootPane().setDefaultButton(null);
        setTitle(getWelcomeFrameTitle());
      });
    }

    @Override
    public void setupFrame(JFrame frame) {

    }

    @Override
    public void dispose() {
      if (myBalloonLayout != null) {
        myBalloonLayout.dispose();
        myBalloonLayout = null;
      }
    }

    private JComponent createUpdatePluginsLink() {
      myUpdatePluginsLink = new LinkLabel<>(IdeBundle.message("updates.plugins.welcome.screen.link.message"), null);
      myUpdatePluginsLink.setVisible(false);

      NonOpaquePanel wrap = new NonOpaquePanel(myUpdatePluginsLink);
      wrap.setBorder(JBUI.Borders.empty(0, 10, 8, 11));
      return wrap;
    }

    public void showPluginUpdates(@NotNull Runnable callback) {
      myUpdatePluginsLink.setListener((__, ___) -> callback.run(), null);
      myUpdatePluginsLink.setVisible(true);
    }

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
    return myScreen.getBalloonLayout();
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

  public static Pair<JPanel, JBList<AnAction>> createActionGroupPanel(final ActionGroup action,
                                                                      final Runnable backAction,
                                                                      @NotNull Disposable parentDisposable) {
    JPanel actionsListPanel = new JPanel(new BorderLayout());
    actionsListPanel.setBackground(getProjectsBackground());
    final List<AnAction> groups = flattenActionGroups(action);
    final DefaultListModel<AnAction> model = JBList.createDefaultListModel(groups);
    final JBList<AnAction> list = new JBList<>(model);
    for (AnAction group : groups) {
      if (group instanceof Disposable) {
        Disposer.register(parentDisposable, (Disposable)group);
      }
    }
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        model.clear();
      }
    });

    list.setBackground(getProjectsBackground());
    list.setCellRenderer(new GroupedItemsListRenderer<AnAction>(new ListItemDescriptorAdapter<AnAction>() {
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
         int index = model.indexOf(value);
         final String parentGroupName = getParentGroupName(value);

         if (index < 1) return parentGroupName != null;
         AnAction upper = model.get(index - 1);
         if (getParentGroupName(upper) == null && parentGroupName != null) return true;

         return !Objects.equals(getParentGroupName(upper), parentGroupName);
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
       protected void customizeComponent(JList<? extends AnAction> list, AnAction value, boolean isSelected) {
         if (myTextLabel != null) {
           myTextLabel.setText(getActionText(value));
           myTextLabel.setIcon(value.getTemplatePresentation().getIcon());
         }
       }
     }
    );

    JScrollPane pane = ScrollPaneFactory.createScrollPane(list, true);
    pane.setBackground(getProjectsBackground());
    actionsListPanel.add(pane, BorderLayout.CENTER);

    int width = (int)MathUtil.clamp(Math.round(list.getPreferredSize().getWidth()), JBUIScale.scale(100), JBUIScale.scale(200));
    pane.setPreferredSize(JBUI.size(width + 14, -1));

    boolean singleProjectGenerator = list.getModel().getSize() == 1;

    final Ref<Component> selected = Ref.create();
    final JPanel main = new JPanel(new BorderLayout());
    main.add(actionsListPanel, BorderLayout.WEST);

    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new JBColor(Gray._217, Gray._81)));
    main.add(bottomPanel, BorderLayout.SOUTH);

    final HashMap<Object, JPanel> panelsMap = new HashMap<>();
    ListSelectionListener selectionListener = e -> {
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
        final JPanel panel = panelsMap.computeIfAbsent(value, o -> ((AbstractActionWithPanel)value).createPanel());
        ((AbstractActionWithPanel)value).onPanelSelected();

        panel.setBorder(JBUI.Borders.empty(7, 10));
        selected.set(panel);
        main.add(selected.get());

        updateBottomPanel(panel, (AbstractActionWithPanel)value, bottomPanel, backAction);

        main.revalidate();
        main.repaint();
      }
    };
    list.addListSelectionListener(selectionListener);
    if (backAction != null) {
      new DumbAwareAction() {
        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(!StackingPopupDispatcher.getInstance().isPopupFocused());
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          backAction.run();
        }
      }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, main, parentDisposable);
    }
    installQuickSearch(list);

    if (singleProjectGenerator) {
      actionsListPanel.setPreferredSize(new Dimension(0, 0));
    }

    return Pair.create(main, list);
  }

  private static void updateBottomPanel(@NotNull JPanel currentPanel,
                                        @NotNull AbstractActionWithPanel actionWithPanel,
                                        @NotNull JPanel bottomPanel,
                                        @Nullable Runnable backAction) {
    bottomPanel.removeAll();

    if (SystemInfo.isMac) {
      addCancelButton(bottomPanel, backAction);
      addActionButton(bottomPanel, actionWithPanel, currentPanel);
    }
    else {
      addActionButton(bottomPanel, actionWithPanel, currentPanel);
      addCancelButton(bottomPanel, backAction);
    }
  }

  private static void addCancelButton(@NotNull JPanel bottomPanel, @Nullable Runnable backAction) {
    JComponent cancelButton = createCancelButton(backAction);
    if (cancelButton != null) {
      bottomPanel.add(cancelButton);
    }
  }

  private static void addActionButton(@NotNull JPanel bottomPanel,
                                      @NotNull AbstractActionWithPanel actionWithPanel,
                                      @NotNull JPanel currentPanel) {
    JButton actionButton = actionWithPanel.getActionButton();
    bottomPanel.add(actionButton);
    currentPanel.getRootPane().setDefaultButton(actionButton);
  }

  @Nullable
  private static JComponent createCancelButton(@Nullable Runnable cancelAction) {
    if (cancelAction == null) return null;

    JButton cancelButton = new JButton(CommonBundle.getCancelButtonText());
    cancelButton.addActionListener(e -> cancelAction.run());

    return cancelButton;
  }

  public static void installQuickSearch(JBList<? extends AnAction> list) {
    new ListSpeedSearch<>(list, (Function<AnAction, String>)o -> {
      if (o instanceof AbstractActionWithPanel) { //to avoid dependency mess with ProjectSettingsStepBase
        return o.getTemplatePresentation().getText();
      }
      return null;
    });
  }

  private static List<AnAction> flattenActionGroups(@NotNull final ActionGroup action) {
    final ArrayList<AnAction> groups = new ArrayList<>();
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

  private static class WelcomeFrameMenuBar extends IdeMenuBar {

    @Override
    public @NotNull ActionGroup getMainMenuActionGroup() {
      return new DefaultActionGroup(ActionManager.getInstance().getAction(GROUP_FILE),
                                    ActionManager.getInstance().getAction(GROUP_HELP_MENU));
    }
  }
}
