// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.panel.ProgressPanel;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class ProcessPopup {
  public static final Key<ProgressPanel> KEY = new Key<>("ProgressPanel");

  private final InfoAndProgressPanel myProgressPanel;
  private final JPanel myIndicatorPanel;
  private final JScrollPane myContentPanel;
  private DialogWrapper myDialog;

  public ProcessPopup(@NotNull InfoAndProgressPanel progressPanel) {
    myProgressPanel = progressPanel;

    myIndicatorPanel = new JBPanelWithEmptyText().withEmptyText(IdeBundle.message("progress.window.empty.text")).andTransparent();
    myIndicatorPanel.setLayout(new VerticalLayout(0));
    myIndicatorPanel.setBorder(JBUI.Borders.empty(2, 0, 6, 0));
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
    myIndicatorPanel.remove(component);
    if (index == 0 && myIndicatorPanel.getComponentCount() > 0) {
      hideSeparator(myIndicatorPanel.getComponent(0));
    }
    revalidateAll();
  }

  public void show(boolean requestFocus) {
    updateContentUI();

    myDialog = new BackgroundDialog(myProgressPanel, myContentPanel, myIndicatorPanel, requestFocus);

    ApplicationManager.getApplication().getMessageBus().connect(myDialog.getDisposable())
      .subscribe(LafManagerListener.TOPIC, source -> updateContentUI());

    JFrame frame = (JFrame)UIUtil.findUltimateParent(myProgressPanel);

    Dimension contentSize = myContentPanel.getPreferredSize();
    Rectangle bounds = frame.getBounds();
    int width = Math.max(bounds.width / 4, contentSize.width);
    int height = Math.min(bounds.height / 4, contentSize.height);

    myContentPanel.setPreferredSize(new Dimension(width, height));

    myDialog.setInitialLocationCallback(() -> {
      int x = bounds.x + bounds.width - width - JBUI.scale(5);
      int y = bounds.y + bounds.height - height - JBUI.scale(40);

      StatusBarEx sb = (StatusBarEx)((IdeFrame)frame).getStatusBar();
      if (sb != null && sb.isVisible()) {
        y -= sb.getSize().height;
      }

      return new Point(x, y);
    });

    myDialog.show();
  }

  public boolean isShowing() {
    return myDialog != null;
  }

  public void hide() {
    if (myDialog != null) {
      myDialog.close(DialogWrapper.CLOSE_EXIT_CODE);
      myDialog = null;
    }
  }

  private void revalidateAll() {
    myContentPanel.doLayout();
    myContentPanel.revalidate();
    myContentPanel.repaint();
  }

  private void updateContentUI() {
    if (myDialog != null) {
      UIUtil.decorateWindowHeader(myDialog.getRootPane());
    }
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

  private class BackgroundDialog extends DialogWrapper {
    private final JComponent myContent;
    private final JComponent myFocus;

    protected BackgroundDialog(@NotNull JComponent parent, @NotNull JComponent content, @NotNull JComponent focus, boolean requestFocus) {
      super(parent, false);
      myContent = content;
      myFocus = focus;

      init();

      setModal(false);
      setResizable(true);
      setTitle(IdeBundle.message("progress.window.title"));
      getWindow().setFocusableWindowState(requestFocus);
    }

    @Override
    public void doCancelAction() {
      myProgressPanel.hideProcessPopup();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      return myContent;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return myFocus;
    }

    @Override
    protected boolean isProgressDialog() {
      return true;
    }

    @Override
    protected @Nullable Border createContentPaneBorder() {
      JBInsets insets = UIUtil.getRegularPanelInsets();
      return new JBEmptyBorder(insets.top, 0, insets.bottom, 0);
    }

    @Override
    protected JComponent createSouthPanel() {
      return null;
    }

    @Override
    protected String getDimensionServiceKey() {
      return "ProcessPopupWindow";
    }
  }
}