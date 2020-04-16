// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ClickListener;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.accessibility.AccessibleContextDelegate;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.*;

public class WelcomeScreenComponentFactory {

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
}
