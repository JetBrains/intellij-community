// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.application.Topics;
import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.diagnostic.MessagePool;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.AboutPopup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.widget.IdeNotificationArea;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.ClickListener;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
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
import java.util.Collections;
import java.util.Objects;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenFocusManager.installFocusable;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.*;
import static com.intellij.util.ui.UIUtil.FontSize.SMALL;

public final class WelcomeScreenComponentFactory {
  @NotNull static JComponent createSmallLogo() {
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();

    NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());

    String welcomeScreenLogoUrl = appInfo.getApplicationSvgIconUrl();
    if (welcomeScreenLogoUrl != null) {
      Icon icon = IconLoader.getIcon(welcomeScreenLogoUrl, WelcomeScreenComponentFactory.class.getClassLoader());
      float scale = 28f / icon.getIconWidth();
      Icon smallLogoIcon = IconUtil.scale(icon, null, scale);
      JLabel logo = new JLabel(smallLogoIcon);
      logo.setBorder(JBUI.Borders.empty(29, 0, 27, 0));
      logo.setHorizontalAlignment(SwingConstants.CENTER);
      panel.add(logo, BorderLayout.WEST);
    }

    String applicationName = Boolean.getBoolean("ide.ui.name.with.edition")
                             ? ApplicationNamesInfo.getInstance().getFullProductNameWithEdition()
                             : ApplicationNamesInfo.getInstance().getFullProductName();
    JLabel appName = new JLabel(applicationName);
    appName.setForeground(JBColor.foreground());
    appName.setFont(appName.getFont().deriveFont(Font.PLAIN));

    ActionLink copyAbout = new ActionLink("", EmptyIcon.ICON_16, createCopyAboutAction());
    copyAbout.setHoveringIcon(AllIcons.Actions.Copy);
    copyAbout.setToolTipText(IdeBundle.message("welcome.screen.copy.about.action.text"));

    String appVersion = appInfo.getFullVersion();

    if (appInfo.isEAP() && !appInfo.getBuild().isSnapshot()) {
      appVersion += " (" + appInfo.getBuild().asStringWithoutProductCode() + ")";
    }

    JLabel version = new JLabel(appVersion);
    version.setFont(UIUtil.getLabelFont(SMALL));
    version.setForeground(Gray._128);
    NonOpaquePanel textPanel = new NonOpaquePanel();
    textPanel.setLayout(new VerticalFlowLayout(0, 0));
    textPanel.setBorder(JBUI.Borders.empty(28, 10, 25, 10));
    JPanel namePanel = JBUI.Panels.simplePanel(appName).andTransparent().addToRight(copyAbout);
    textPanel.add(namePanel);
    textPanel.add(version);
    panel.add(textPanel, BorderLayout.CENTER);
    panel.setToolTipText(applicationName + " " + appVersion);

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

  @NotNull static JComponent createLogo() {
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();

    NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());

    String welcomeScreenLogoUrl = appInfo.getWelcomeScreenLogoUrl();
    if (welcomeScreenLogoUrl != null) {
      JLabel logo = new JLabel(IconLoader.getIcon(welcomeScreenLogoUrl, WelcomeScreenComponentFactory.class.getClassLoader()));
      logo.setBorder(JBUI.Borders.empty(30, 0, 10, 0));
      logo.setHorizontalAlignment(SwingConstants.CENTER);
      panel.add(logo, BorderLayout.NORTH);
    }

    String applicationName = Boolean.getBoolean("ide.ui.name.with.edition")
                             ? ApplicationNamesInfo.getInstance().getFullProductNameWithEdition()
                             : ApplicationNamesInfo.getInstance().getFullProductName();
    JLabel appName = new JLabel(applicationName);
    appName.setForeground(JBColor.foreground());
    appName.setFont(getProductFont(36).deriveFont(Font.PLAIN));
    appName.setHorizontalAlignment(SwingConstants.CENTER);
    String appVersion = IdeBundle.message("welcome.screen.logo.version.label", appInfo.getFullVersion());

    if (appInfo.isEAP() && !appInfo.getBuild().isSnapshot()) {
      appVersion += " (" + appInfo.getBuild().asStringWithoutProductCode() + ")";
    }

    JLabel version = new JLabel(appVersion);
    version.setFont(getProductFont(16));
    version.setHorizontalAlignment(SwingConstants.CENTER);
    version.setForeground(Gray._128);

    panel.add(appName);
    panel.add(version, BorderLayout.SOUTH);
    panel.setBorder(JBUI.Borders.emptyBottom(20));
    return panel;
  }

  private static AnAction createCopyAboutAction() {
    return DumbAwareAction.create(e -> {
      CopyPasteManager.getInstance().setContents(new StringSelection(AboutPopup.getAboutText()));
    });
  }

  static JComponent createRecentProjects(Disposable parentDisposable) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new NewRecentProjectPanel(parentDisposable), BorderLayout.CENTER);
    panel.setBackground(getProjectsBackground());
    panel.setBorder(new CustomLineBorder(getSeparatorColor(), JBUI.insetsRight(1)));
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
    @NotNull private final ActionLink myActionLink;

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

  @NotNull
  public static AnAction createShowPopupAction(@NonNls @NotNull String groupId) {
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
    installFocusable(parentContainer, panel, action, KeyEvent.VK_DOWN, KeyEvent.VK_UP, focusOnLeft);
    return panel;
  }

  public static JComponent wrapActionLink(@NotNull ActionLink link) {
    JPanel panel = (JPanel)wrapActionLinkWithoutArrow(link);
    if (!StringUtil.isEmptyOrSpaces(link.getText())) {
      panel.add(createArrow(link), BorderLayout.EAST);
    }
    return panel;
  }

  public static JComponent wrapActionLinkWithoutArrow(@NotNull ActionLink link) {
    // Don't allow focus, as the containing panel is going to be focusable.
    link.setFocusable(false);
    link.setPaintUnderline(false);
    link.setNormalColor(getLinkNormalColor());
    JActionLinkPanel panel = new JActionLinkPanel(link);
    panel.setBorder(JBUI.Borders.empty(4, 6));
    return panel;
  }

  public static JComponent createErrorsLink(Disposable parent) {
    IdeMessagePanel panel = new IdeMessagePanel(null, MessagePool.getInstance());
    panel.setBorder(JBUI.Borders.emptyRight(13));
    panel.setOpaque(false);
    Disposer.register(parent, panel);
    return panel;
  }

  @NotNull
  public static Component createEventLink(@NotNull @Nls String linkText, @NotNull Disposable parentDisposable) {
    ActionLink actionLink = new ActionLink(linkText, AllIcons.Ide.Notification.NoEvents, new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        BalloonLayout balloonLayout = WelcomeFrame.getInstance().getBalloonLayout();
        if (balloonLayout instanceof WelcomeBalloonLayoutImpl) {
          ((WelcomeBalloonLayoutImpl)balloonLayout).showPopup();
        }
      }
    });
    final JComponent panel = wrapActionLink(actionLink);
    panel.setVisible(false);
    Topics.subscribe(WelcomeBalloonLayoutImpl.BALLOON_NOTIFICATION_TOPIC, parentDisposable, types -> {
      BalloonLayout balloonLayout = WelcomeFrame.getInstance().getBalloonLayout();
      if (balloonLayout instanceof WelcomeBalloonLayoutImpl) {
        WelcomeBalloonLayoutImpl welcomeBalloonLayout = (WelcomeBalloonLayoutImpl)balloonLayout;
        if (welcomeBalloonLayout.getLocationComponent() == null) {
          welcomeBalloonLayout.setLocationComponent(actionLink);
        }
      }
      if (!types.isEmpty()) {
        NotificationType type = Collections.max(types);
        actionLink.setIcon(type == NotificationType.IDE_UPDATE
                           ? AllIcons.Ide.Notification.IdeUpdate
                           : IdeNotificationArea.createIconWithNotificationCount(panel, type, types.size(), false));
      }
      panel.setVisible(!types.isEmpty());
    });
    return panel;
  }

  @Nls
  public static String getApplicationTitle() {
    String title = IdeBundle.message("label.welcome.to.0", ApplicationNamesInfo.getInstance().getFullProductName());
    if (Boolean.getBoolean("ide.ui.version.in.title")) {
      title += ' ' + ApplicationInfo.getInstance().getFullVersion();
    }
    String suffix = ProjectFrameHelper.getSuperUserSuffix();
    if (suffix != null) {
      title += " (" + suffix + ")";
    }
    return title;
  }

  public static @NotNull JPanel createNotificationPanel(@NotNull Disposable parentDisposable) {
    JPanel panel = new NonOpaquePanel(new FlowLayout(FlowLayout.RIGHT));
    panel.setBorder(JBUI.Borders.emptyTop(10));
    panel.add(createErrorsLink(parentDisposable));
    panel.add(createEventLink("", parentDisposable));
    return panel;
  }
}
