// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.application.Topics;
import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.diagnostic.MessagePool;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.widget.IdeNotificationArea;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextDelegate;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenFocusManager.installFocusable;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.*;
import static com.intellij.util.ui.UIUtil.FontSize.SMALL;

public class WelcomeScreenComponentFactory {

  @NotNull
  static JComponent createSmallLogo() {
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();

    NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());

    String welcomeScreenLogoUrl = appInfo.getApplicationSvgIconUrl();
    if (welcomeScreenLogoUrl != null) {
      Icon icon = IconLoader.getIcon(welcomeScreenLogoUrl);
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
    appName.setHorizontalAlignment(SwingConstants.CENTER);

    String appVersion = appInfo.getFullVersion();

    if (appInfo.isEAP() && !appInfo.getBuild().isSnapshot()) {
      appVersion += " (" + appInfo.getBuild().asStringWithoutProductCode() + ")";
    }

    JLabel version = new JLabel(appVersion);
    version.setFont(UIUtil.getLabelFont(SMALL));
    version.setHorizontalAlignment(SwingConstants.CENTER);
    version.setForeground(Gray._128);
    NonOpaquePanel textPanel = new NonOpaquePanel();
    textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
    textPanel.setBorder(JBUI.Borders.empty(28, 10, 25, 10));
    textPanel.add(appName);
    textPanel.add(version);
    panel.add(textPanel, BorderLayout.CENTER);
    return panel;
  }

  @NotNull
  static JComponent createLogo() {
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();

    NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());

    String welcomeScreenLogoUrl = appInfo.getWelcomeScreenLogoUrl();
    if (welcomeScreenLogoUrl != null) {
      JLabel logo = new JLabel(IconLoader.getIcon(welcomeScreenLogoUrl));
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
    String appVersion = "Version ";

    appVersion += appInfo.getFullVersion();

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

  static JComponent createRecentProjects(Disposable parentDisposable) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new NewRecentProjectPanel(parentDisposable), BorderLayout.CENTER);
    panel.setBackground(getProjectsBackground());
    panel.setBorder(new CustomLineBorder(getSeparatorColor(), JBUI.insetsRight(1)));
    return panel;
  }

  static JLabel createArrow(final ActionLink link) {
    JLabel arrow = new JLabel(AllIcons.General.ArrowDown);
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

  /**
   * Wraps an {@link ActionLink} component and delegates accessibility support to it.
   */
  protected static class JActionLinkPanel extends JPanel {
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

  static JComponent createActionLink(@NotNull Container parentContainer,
                                     @Nls String text,
                                     final String groupId,
                                     Icon icon,
                                     @Nullable Component focusOnLeft) {
    final Ref<ActionLink> ref = new Ref<>(null);
    AnAction action = new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ActionGroup configureGroup = (ActionGroup)ActionManager.getInstance().getAction(groupId);
        PopupFactoryImpl.ActionGroupPopup popup = new PopupFactoryImpl.ActionGroupPopup(
          null, configureGroup, e.getDataContext(),
          false, false, false, false, null, -1, null,
          ActionPlaces.WELCOME_SCREEN,
          new MenuItemPresentationFactory(true), false);
        popup.showUnderneathOfLabel(ref.get());
      }
    };
    JComponent panel = createActionLink(text, icon, ref, action);
    installFocusable(parentContainer, panel, action, KeyEvent.VK_DOWN, KeyEvent.VK_UP, focusOnLeft);
    return panel;
  }

  static JComponent createActionLink(@Nls String text, Icon icon, Ref<? super ActionLink> ref, AnAction action) {
    ActionLink link = new ActionLink(text, icon, action);
    ref.set(link);
    // Don't allow focus, as the containing panel is going to be focusable.
    link.setFocusable(false);
    link.setPaintUnderline(false);
    link.setNormalColor(getLinkNormalColor());
    JActionLinkPanel panel = new JActionLinkPanel(link);
    panel.setBorder(JBUI.Borders.empty(4, 6));
    if (!StringUtil.isEmptyOrSpaces(text)) {
      panel.add(createArrow(link), BorderLayout.EAST);
    }
    return panel;
  }

  static JComponent createErrorsLink(Disposable parent) {
    IdeMessagePanel panel = new IdeMessagePanel(null, MessagePool.getInstance());
    panel.setBorder(JBUI.Borders.emptyRight(13));
    panel.setOpaque(false);
    Disposer.register(parent, panel);
    return panel;
  }

  @NotNull
  static Component createEventLink(@NotNull @Nls String linkText, @NotNull Disposable parentDisposable) {
    final Ref<ActionLink> actionLinkRef = new Ref<>();
    final JComponent panel = createActionLink(linkText, AllIcons.Ide.Notification.NoEvents, actionLinkRef, new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        BalloonLayout balloonLayout = WelcomeFrame.getInstance().getBalloonLayout();
        if (balloonLayout instanceof WelcomeBalloonLayoutImpl) {
          WelcomeBalloonLayoutImpl welcomeBalloonLayout = (WelcomeBalloonLayoutImpl)balloonLayout;
          if (welcomeBalloonLayout.getLocationComponent() == null && e.getInputEvent() != null) {
            welcomeBalloonLayout.setLocationComponent(e.getInputEvent().getComponent());
          }
          welcomeBalloonLayout.showPopup();
        }
      }
    });
    panel.setVisible(false);
    Topics.subscribe(WelcomeBalloonLayoutImpl.BALLOON_NOTIFICATION_TOPIC, parentDisposable, types -> {
      if (!types.isEmpty()) {
        NotificationType type = Collections.max(types);
        actionLinkRef.get().setIcon(IdeNotificationArea.createIconWithNotificationCount(actionLinkRef.get(), type, types.size(), false));
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
}
