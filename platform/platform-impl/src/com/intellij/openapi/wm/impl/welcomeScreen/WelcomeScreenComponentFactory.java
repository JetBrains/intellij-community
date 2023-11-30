// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.diagnostic.MessagePool;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.AboutDialog;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.widget.IdeNotificationArea;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.StartPagePromoter;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.SelectablePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IconUtil;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextDelegate;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

public final class WelcomeScreenComponentFactory {
  static @NotNull JComponent createSmallLogo() {
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();

    NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());

    String welcomeScreenLogoUrl = appInfo.getApplicationSvgIconUrl();
    Icon icon = IconLoader.getIcon(welcomeScreenLogoUrl, WelcomeScreenComponentFactory.class.getClassLoader());
    JLabel logo = new JLabel() {
      @Override
      public void updateUI() {
        super.updateUI();
        float scale = JBUIScale.scale(28f) / icon.getIconWidth();
        Icon smallLogoIcon = IconUtil.scale(icon, null, scale);
        setIcon(smallLogoIcon);
      }
    };
    logo.setBorder(JBUI.Borders.empty(29, 0, 27, 0));
    logo.setHorizontalAlignment(SwingConstants.CENTER);
    panel.add(logo, BorderLayout.WEST);

    String applicationName = getAppName();
    JLabel appName = new JLabel(applicationName);
    appName.setForeground(JBColor.foreground());
    appName.setFont(JBFont.create(appName.getFont().deriveFont(Font.PLAIN), false));

    ActionLink copyAbout = new ActionLink("", EmptyIcon.ICON_16, createCopyAboutAction());
    copyAbout.setHoveringIcon(AllIcons.Actions.Copy);
    copyAbout.setToolTipText(IdeBundle.message("welcome.screen.copy.about.action.text"));

    String appVersion = appInfo.getFullVersion();

    if (appInfo.isEAP() && !appInfo.getBuild().isSnapshot()) {
      appVersion += " (" + appInfo.getBuild().asStringWithoutProductCode() + ")";
    }

    JLabel version = new JLabel(appVersion);
    version.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    version.setForeground(ExperimentalUI.isNewUI() ? JBUI.CurrentTheme.ContextHelp.FOREGROUND : Gray._128);
    NonOpaquePanel textPanel = new NonOpaquePanel();
    textPanel.setLayout(new VerticalFlowLayout(0, 0));
    textPanel.setBorder(JBUI.Borders.empty(28, 10, 25, 10));
    JPanel namePanel = JBUI.Panels.simplePanel(appName).andTransparent().addToRight(copyAbout);
    textPanel.add(namePanel);
    textPanel.add(version);
    panel.add(textPanel, BorderLayout.CENTER);
    panel.setToolTipText(IdeBundle.message("about.box.build.number", appInfo.getBuild()));

    panel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        copyAbout.setIcon(AllIcons.Actions.Copy);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        copyAbout.setIcon(EmptyIcon.ICON_16);
      }
    });
    return panel;
  }

  static @NotNull JComponent createLogo() {
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();

    NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());

    JLabel logo = new JLabel(IconLoader.getIcon(appInfo.getApplicationSvgIconUrl(), WelcomeScreenComponentFactory.class.getClassLoader()));
    logo.setBorder(JBUI.Borders.empty(30, 0, 10, 0));
    logo.setHorizontalAlignment(SwingConstants.CENTER);
    panel.add(logo, BorderLayout.NORTH);

    JLabel appName = new JLabel(getAppName());
    appName.setForeground(JBColor.foreground());
    appName.setFont(WelcomeScreenUIManager.getProductFont(36).deriveFont(Font.PLAIN));
    appName.setHorizontalAlignment(SwingConstants.CENTER);
    String appVersion = IdeBundle.message("welcome.screen.logo.version.label", appInfo.getFullVersion());

    if (appInfo.isEAP() && !appInfo.getBuild().isSnapshot()) {
      appVersion += " (" + appInfo.getBuild().asStringWithoutProductCode() + ")";
    }

    JLabel version = new JLabel(appVersion);
    version.setFont(WelcomeScreenUIManager.getProductFont(16));
    version.setHorizontalAlignment(SwingConstants.CENTER);
    version.setForeground(Gray._128);

    panel.add(appName);
    panel.add(version, BorderLayout.SOUTH);
    panel.setBorder(JBUI.Borders.emptyBottom(20));
    return panel;
  }

  private static AnAction createCopyAboutAction() {
    return DumbAwareAction.create(e -> {
      CopyPasteManager.getInstance().setContents(new StringSelection(new AboutDialog(null).getExtendedAboutText()));
    });
  }

  static JComponent createRecentProjects(Disposable parentDisposable) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new NewRecentProjectPanel(parentDisposable), BorderLayout.CENTER);
    panel.setBackground(WelcomeScreenUIManager.getProjectsBackground());
    panel.setBorder(new CustomLineBorder(WelcomeScreenUIManager.getSeparatorColor(), JBUI.insetsRight(1)));
    return panel;
  }

  static JLabel createArrow(final ActionLink link) {
    JLabel arrow = new JLabel(AllIcons.General.LinkDropTriangle);
    arrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

  /**
   * Wraps an {@link ActionLink} component and delegates accessibility support to it.
   */
  protected static final class JActionLinkPanel extends JPanel {
    private final @NotNull ActionLink myActionLink;

    public JActionLinkPanel(@NotNull ActionLink actionLink) {
      super(new BorderLayout());
      myActionLink = actionLink;
      add(myActionLink);
      setOpaque(false);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleJActionLinkPanel(myActionLink.getAccessibleContext());
      }
      return accessibleContext;
    }

    protected final class AccessibleJActionLinkPanel extends AccessibleContextDelegate {
      private final AccessibleJComponent myAccessibleHelper = new AccessibleJComponent() {
      };

      AccessibleJActionLinkPanel(AccessibleContext context) {
        super(context);
      }

      @Override
      public Container getDelegateParent() {
        return getParent();
      }

      @Override
      public AccessibleRole getAccessibleRole() {
        return AccessibleRole.PUSH_BUTTON;
      }

      @Override
      public int getAccessibleChildrenCount() {
        return myAccessibleHelper.getAccessibleChildrenCount();
      }

      @Override
      public Accessible getAccessibleChild(int i) {
        return myAccessibleHelper.getAccessibleChild(i);
      }
    }
  }

  public static @NotNull AnAction createShowPopupAction(@NonNls @NotNull String groupId) {
    return new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ActionGroup configureGroup = (ActionGroup)ActionManager.getInstance().getAction(groupId);
        PopupFactoryImpl.ActionGroupPopup popup = new PopupFactoryImpl.ActionGroupPopup(
          null, configureGroup, e.getDataContext(),
          false, false, false, false, null, -1, null,
          ActionPlaces.WELCOME_SCREEN,
          new MenuItemPresentationFactory(true), false);
        popup.showUnderneathOf(Objects.requireNonNull(e.getInputEvent().getComponent()));
      }
    };
  }

  static JComponent createActionLink(@NotNull Container parentContainer,
                                     @Nls String text,
                                     final String groupId,
                                     Icon icon,
                                     @Nullable Component focusOnLeft) {
    AnAction action = createShowPopupAction(groupId);
    JComponent panel = wrapActionLink(new ActionLink(text, icon, action));
    WelcomeScreenFocusManager.installFocusable(parentContainer, panel, action, KeyEvent.VK_DOWN, KeyEvent.VK_UP, focusOnLeft);
    return panel;
  }

  public static JComponent wrapActionLink(@NotNull ActionLink link) {
    JPanel panel = wrapActionLinkWithoutArrow(link);
    if (!StringUtil.isEmptyOrSpaces(link.getText())) {
      panel.add(createArrow(link), BorderLayout.EAST);
    }
    return panel;
  }

  public static JActionLinkPanel wrapActionLinkWithoutArrow(@NotNull ActionLink link) {
    // Don't allow focus, as the containing panel is going to be focusable.
    link.setFocusable(false);
    link.setPaintUnderline(false);
    link.setNormalColor(WelcomeScreenUIManager.getLinkNormalColor());
    JActionLinkPanel panel = new JActionLinkPanel(link);
    panel.setBorder(JBUI.Borders.empty(4, 6));
    return panel;
  }

  public static JComponent createErrorsLink(@NotNull Disposable parent) {
    IdeMessagePanel panel = new IdeMessagePanel(null, MessagePool.getInstance());
    panel.getComponent().setBorder(JBUI.Borders.emptyRight(13));
    Disposer.register(parent, panel);
    return panel.getComponent();
  }

  /**
   *
   * @deprecated use {@link NotificationEventAction} instead
   */
  @Deprecated
  public static @NotNull JComponent createEventLink(@NotNull @Nls String linkText, @NotNull SimpleMessageBusConnection busConnection) {
    SelectablePanel selectablePanel = new SelectablePanel();
    ActionLink actionLink = new ActionLink(linkText, getNotificationIcon(Collections.emptyList(), null), new DumbAwareAction() {
      private boolean hideListenerInstalled = false;

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        BalloonLayout balloonLayout = WelcomeFrame.getInstance().getBalloonLayout();
        if (balloonLayout instanceof WelcomeBalloonLayoutImpl welcomeBalloonLayout) {
          if (!hideListenerInstalled) {
            welcomeBalloonLayout.setHideListener(() -> selectablePanel.setSelectionColor(null));
            hideListenerInstalled = true;
          }
          selectablePanel.setSelectionColor(JBUI.CurrentTheme.ActionButton.pressedBackground());
          welcomeBalloonLayout.showPopup();
        }
      }
    });

    JComponent panel = wrapActionLink(actionLink);
    selectablePanel.setLayout(new BorderLayout());
    selectablePanel.add(panel);
    selectablePanel.setOpaque(false);
    selectablePanel.setBorder(panel.getBorder());
    selectablePanel.setSelectionArc(JBUIScale.scale(6));
    panel.setBorder(null);
    selectablePanel.setVisible(false);

    busConnection.subscribe(WelcomeBalloonLayoutImpl.BALLOON_NOTIFICATION_TOPIC, types -> {
      BalloonLayout balloonLayout = WelcomeFrame.getInstance().getBalloonLayout();
      if (balloonLayout instanceof WelcomeBalloonLayoutImpl welcomeBalloonLayout) {
        if (welcomeBalloonLayout.getLocationComponent() == null) {
          welcomeBalloonLayout.setLocationComponent(actionLink);
        }
      }
      if (!types.isEmpty()) {
        actionLink.setIcon(getNotificationIcon(types, selectablePanel));
      }
      selectablePanel.setVisible(!types.isEmpty());
    });
    return selectablePanel;
  }

  private static final BadgeIconSupplier NOTIFICATION_ICON = new BadgeIconSupplier(AllIcons.Toolwindows.Notifications);

  static Icon getNotificationIcon(List<NotificationType> notificationTypes, JComponent panel) {
    if (ExperimentalUI.isNewUI()) {
      return IconUtil.resizeSquared(NOTIFICATION_ICON.getInfoIcon(!notificationTypes.isEmpty()), JBUIScale.scale(16));
    }
    else {
      if (notificationTypes.isEmpty()) {
        return AllIcons.Ide.Notification.NoEvents;
      }

      NotificationType type = Collections.max(notificationTypes);
      return type == NotificationType.IDE_UPDATE
             ? AllIcons.Ide.Notification.IdeUpdate
             : IdeNotificationArea.createIconWithNotificationCount(panel, type, notificationTypes.size(), false);
    }
  }

  public static @Nls String getApplicationTitle() {
    String title = IdeBundle.message("label.welcome.to.0", ApplicationNamesInfo.getInstance().getFullProductName());
    if (Boolean.getBoolean("ide.ui.version.in.title")) {
      title += ' ' + ApplicationInfo.getInstance().getFullVersion();
    }
    String suffix = ProjectFrameHelper.Companion.getSuperUserSuffix();
    if (suffix != null) {
      title += " (" + suffix + ")";
    }
    return title;
  }

  public static @NotNull JComponent createNotificationToolbar(@NotNull Disposable parentDisposable) {
    var horizontalGap = 4;

    IdeMessagePanel panel = new IdeMessagePanel(null, MessagePool.getInstance());
    Disposer.register(parentDisposable, panel);

    DefaultActionGroup group = new DefaultActionGroup(panel.getAction(), new NotificationEventAction(parentDisposable));
    ActionToolbarImpl toolbar = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(
      "WelcomeScreen.NotificationPanel", group, true);
    toolbar.setMinimumButtonSize(new JBDimension(26, 26));
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.setActionButtonBorder(horizontalGap, 1);

    ApplicationManager.getApplication().getMessageBus().connect(parentDisposable)
      .subscribe(WelcomeBalloonLayoutImpl.BALLOON_NOTIFICATION_TOPIC, new WelcomeBalloonLayoutImpl.BalloonNotificationListener() {
        @Override
        public void notificationsChanged(List<NotificationType> types) {
        }

        @Override
        public void newNotifications() {
          UIUtil.invokeLaterIfNeeded(() -> {
            Disposable disposable = Disposer.newDisposable(parentDisposable);
            toolbar.addListener(new ActionToolbarListener() {
              @Override
              public void actionsUpdated() {
                Disposer.dispose(disposable);
                BalloonLayout balloonLayout = WelcomeFrame.getInstance().getBalloonLayout();
                if (balloonLayout instanceof WelcomeSeparateBalloonLayoutImpl layout) {
                  layout.autoPopup();
                }
              }
            }, disposable);
            toolbar.updateActionsAsync();
          });
        }
      });

    JComponent result = toolbar.getComponent();
    toolbar.setTargetComponent(result);
    result.setOpaque(false);
    if (ExperimentalUI.isNewUI()) {
      result.setBorder(JBUI.Borders.empty(0, 0, 15, 24 - horizontalGap));
    }
    else {
      result.setBorder(JBUI.Borders.empty(0, 0, 5, 8));
    }
    return result;
  }

  /**
   * @deprecated Use {@link #createNotificationToolbar(Disposable)} instead
   */
  @Deprecated
  public static @NotNull JPanel createNotificationPanel(@NotNull Disposable parentDisposable) {
    JComponent errorsLink = createErrorsLink(parentDisposable);
    JComponent eventLink = createEventLink("", ApplicationManager.getApplication().getMessageBus().connect(parentDisposable));
    JPanel panel = new NonOpaquePanel();
    if (ExperimentalUI.isNewUI()) {
      panel.setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 0));
      panel.setBorder(JBUI.Borders.empty(10, 0, 16, 24));
      errorsLink.setBorder(JBUI.Borders.empty(5, 5, 5, 13));
      eventLink.setBorder(JBUI.Borders.empty(5));
    }
    else {
      panel.setLayout(new FlowLayout(FlowLayout.RIGHT));
      panel.setBorder(JBUI.Borders.empty(10, 0, 0, 3));
    }
    panel.add(errorsLink);
    panel.add(eventLink);
    return panel;
  }

  static @Nullable JComponent getSinglePromotion(boolean isEmptyState) {
    StartPagePromoter[] extensions = StartPagePromoter.START_PAGE_PROMOTER_EP.getExtensions();
    List<StartPagePromoter> promoters = new ArrayList<>();
    int maxPriorityLevel = Integer.MIN_VALUE;

    for (StartPagePromoter promoter : extensions) {
      int priorityLevel = promoter.getPriorityLevel();
      if (priorityLevel >= maxPriorityLevel && promoter.canCreatePromo(isEmptyState)) {
        if (priorityLevel > maxPriorityLevel) {
          maxPriorityLevel = priorityLevel;
          promoters.clear();
        }
        promoters.add(promoter);
      }
    }

    if (promoters.isEmpty()) {
      return null;
    }

    StartPagePromoter selectedPromoter = promoters.get(new Random().nextInt(promoters.size()));
    return selectedPromoter.getPromotion(isEmptyState);
  }

  public static @NlsSafe String getAppName() {
    return Boolean.getBoolean("ide.ui.name.with.edition")
           ? ApplicationNamesInfo.getInstance().getFullProductNameWithEdition()
           : ApplicationNamesInfo.getInstance().getFullProductName();
  }
}
