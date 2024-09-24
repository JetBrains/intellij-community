// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.panel.ProgressPanel;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.MinimizeButton;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

final class ProcessPopup {
  public static final Key<ProgressPanel> KEY = new Key<>("ProgressPanel");
  private static final String DIMENSION_SERVICE_KEY = "ProcessPopupWindow";

  private final InfoAndProgressPanel myProgressPanel;
  private final JPanel myIndicatorPanel;
  private final JScrollPane myContentPanel;
  private JBPopup myPopup;
  private boolean myPopupVisible;

  ProcessPopup(@NotNull InfoAndProgressPanel progressPanel) {
    myProgressPanel = progressPanel;

    myIndicatorPanel = new JBPanelWithEmptyText(new VerticalLayout(0)).withEmptyText(IdeBundle.message("progress.window.empty.text")).andTransparent();
    myIndicatorPanel.setBorder(JBUI.Borders.empty(10, 0, 18, 0));
    myIndicatorPanel.setFocusable(true);
    if (ExperimentalUI.isNewUI()) {
      myIndicatorPanel.setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);
    }

    myContentPanel = new JBScrollPane(myIndicatorPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    updateContentUI();
  }

  public void addIndicator(@NotNull InlineProgressIndicator indicator) {
    JComponent component = indicator.getComponent();
    if (myIndicatorPanel.getComponentCount() == 0) {
      hideSeparator(component);
    }
    if (ExperimentalUI.isNewUI()) {
      component.setOpaque(false);
    }
    myIndicatorPanel.add(component);
    revalidateAll();
  }

  public void removeIndicator(@NotNull InlineProgressIndicator indicator) {
    JComponent component = indicator.getComponent();
    int index = myIndicatorPanel.getComponentZOrder(component);
    if (index == -1) {
      return;
    }
    myIndicatorPanel.remove(component);
    if (index == 0 && myIndicatorPanel.getComponentCount() > 0) {
      hideSeparator(myIndicatorPanel.getComponent(0));
    }
    revalidateAll();
  }

  private @NotNull Rectangle calculateBounds() {
    JFrame frame = (JFrame)ComponentUtil.findUltimateParent(myProgressPanel.getComponent());

    Dimension contentSize = myContentPanel.getPreferredSize();
    int contentWidth = Math.max(contentSize.width, JBUI.scale(300));
    int contentHeight = Math.max(contentSize.height, JBUI.scale(100));

    int titleHeight = 0;
    if (myPopup instanceof AbstractPopup) {
      titleHeight = ((AbstractPopup)myPopup).getHeaderPreferredSize().height;
    }

    Rectangle frameBounds = frame.getBounds();
    int fullHeight = frameBounds.height - titleHeight;

    boolean isEmpty = myIndicatorPanel.getComponentCount() == 0;
    int width = Math.max(frameBounds.width / 4, contentWidth);
    int height = Math.min(isEmpty ? frameBounds.height / 4 : fullHeight, contentHeight);

    int x = frameBounds.x + frameBounds.width - width - JBUI.scale(20);
    int y = frameBounds.y + frameBounds.height - height;

    if (height != fullHeight) {
      y -= JBUI.scale(10);
    }

    StatusBarEx sb = (StatusBarEx)((IdeFrame)frame).getStatusBar();
    if (sb != null && sb.isVisible()) {
      int statusBarHeight = sb.getSize().height;
      if (height == fullHeight) {
        height -= statusBarHeight + JBUI.scale(10);
      }
      else {
        y -= statusBarHeight;
      }
    }

    y -= titleHeight;

    return new Rectangle(x, y, width, height);
  }

  public void show(boolean requestFocus) {
    updateContentUI();

    createPopup(myContentPanel, myIndicatorPanel, requestFocus);

    ApplicationManager.getApplication().getMessageBus().connect(myPopup).subscribe(LafManagerListener.TOPIC, source -> updateContentUI());

    Rectangle popupBounds = calculateBounds();
    myContentPanel.setPreferredSize(popupBounds.getSize());
    myPopupVisible = true;
    myPopup.showInScreenCoordinates(myProgressPanel.getComponent().getRootPane(), popupBounds.getLocation());
  }

  public boolean isShowing() {
    return myPopupVisible;
  }

  public void hide() {
    if (myPopup != null) {
      myPopupVisible = false;
      myPopup.cancel();
      myPopup = null;
      myContentPanel.setPreferredSize(null);
    }
  }

  private void revalidateAll() {
    myContentPanel.doLayout();
    myContentPanel.revalidate();
    myContentPanel.repaint();
  }

  private void updateContentUI() {
    IJSwingUtilities.updateComponentTreeUI(myContentPanel);
    myContentPanel.getViewport().setBackground(myIndicatorPanel.getBackground());
    myContentPanel.setBorder(null);
  }

  private static void hideSeparator(@NotNull Component component) {
    ProgressPanel panel = ClientProperty.get(component, KEY);
    if (panel != null) {
      panel.setSeparatorEnabled(false);
    }
  }

  private void createPopup(@NotNull JComponent content, @NotNull JComponent focus, boolean requestFocus) {
    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(content, focus);
    builder.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        myProgressPanel.hideProcessPopup();
      }
    });

    builder.setNormalWindowLevel(true);
    builder.setMovable(true);
    builder.setResizable(true);
    builder.setTitle(IdeBundle.message("progress.window.title"));
    builder.setCancelOnClickOutside(false);
    builder.setRequestFocus(requestFocus);
    builder.setBelongsToGlobalPopupStack(false);
    builder.setMinSize(new JBDimension(300, 100));
    Project project = ProjectUtil.getProjectForComponent(myProgressPanel.getComponent());
    builder.setDimensionServiceKey(project, DIMENSION_SERVICE_KEY, true);
    builder.setLocateWithinScreenBounds(false);

    builder.setCancelButton(new MinimizeButton(IdeBundle.message("tooltip.hide")));

    myPopup = builder.createPopup();
  }
}