// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.panel.ProgressPanel;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.MinimizeButton;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

class ProcessPopup {
  public static final Key<ProgressPanel> KEY = new Key<>("ProgressPanel");

  private final InfoAndProgressPanel myProgressPanel;
  private final JPanel myIndicatorPanel;
  private final JScrollPane myContentPanel;
  private JBPopup myPopup;

  ProcessPopup(@NotNull InfoAndProgressPanel progressPanel) {
    myProgressPanel = progressPanel;

    myIndicatorPanel = new JBPanelWithEmptyText().withEmptyText(IdeBundle.message("progress.window.empty.text")).andTransparent();
    myIndicatorPanel.setLayout(new VerticalLayout(0));
    myIndicatorPanel.setBorder(JBUI.Borders.empty(10, 0, 18, 0));
    myIndicatorPanel.setFocusable(true);

    myContentPanel = new JBScrollPane(myIndicatorPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER) {
      @Override
      public Dimension getPreferredSize() {
        if (myIndicatorPanel.getComponentCount() > 0) {
          return super.getPreferredSize();
        }
        return getEmptyPreferredSize();
      }
    };
    updateContentUI();
  }

  public void addIndicator(@NotNull InlineProgressIndicator indicator) {
    JComponent component = indicator.getComponent();
    if (myIndicatorPanel.getComponentCount() == 0) {
      hideSeparator(component);
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

  public void show(boolean requestFocus) {
    updateContentUI();

    createPopup(myContentPanel, myIndicatorPanel, requestFocus);

    ApplicationManager.getApplication().getMessageBus().connect(myPopup).subscribe(LafManagerListener.TOPIC, source -> updateContentUI());

    JFrame frame = (JFrame)UIUtil.findUltimateParent(myProgressPanel);

    Dimension contentSize = myContentPanel.getPreferredSize();
    Rectangle bounds = frame.getBounds();
    int width = Math.max(bounds.width / 4, contentSize.width);
    int height = Math.min(bounds.height / 4, contentSize.height);

    myContentPanel.setPreferredSize(new Dimension(width, height));

    int x = bounds.x + bounds.width - width - JBUI.scale(20);
    int y = bounds.y + bounds.height - height - JBUI.scale(40);

    StatusBarEx sb = (StatusBarEx)((IdeFrame)frame).getStatusBar();
    if (sb != null && sb.isVisible()) {
      y -= sb.getSize().height;
    }

    myPopup.showInScreenCoordinates(myProgressPanel.getRootPane(), new Point(x, y));
  }

  public boolean isShowing() {
    return myPopup != null;
  }

  public void hide() {
    if (myPopup != null) {
      JBPopup popup = myPopup;
      myPopup = null;
      popup.cancel();
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
    UIUtil.getClientProperty(component, KEY).setSeparatorEnabled(false);
  }

  @NotNull
  private static Dimension getEmptyPreferredSize() {
    Dimension size = ScreenUtil.getMainScreenBounds().getSize();
    size.width *= 0.3d;
    size.height *= 0.3d;
    return size;
  }

  private void createPopup(@NotNull JComponent content, @NotNull JComponent focus, boolean requestFocus) {
    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(content, focus);
    builder.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        myProgressPanel.hideProcessPopup();
      }
    });

    builder.setMovable(true);
    builder.setResizable(true);
    builder.setTitle(IdeBundle.message("progress.window.title"));
    builder.setDimensionServiceKey(null, "ProcessPopupWindow", true);
    builder.setCancelOnClickOutside(false);
    builder.setRequestFocus(requestFocus);
    builder.setBelongsToGlobalPopupStack(false);
    builder.setLocateByContent(true);
    builder.setMinSize(new JBDimension(300, 100));

    builder.setCancelButton(new MinimizeButton("Hide"));

    myPopup = builder.addUserData("SIMPLE_WINDOW").createPopup();
    myPopup.getContent().putClientProperty(AbstractPopup.FIRST_TIME_SIZE, new JBDimension(300, 0));
  }
}