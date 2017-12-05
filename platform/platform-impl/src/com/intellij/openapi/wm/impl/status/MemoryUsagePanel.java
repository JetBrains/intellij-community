// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MemoryUsagePanel extends JButton implements CustomStatusBarWidget {
  @NonNls public static final String WIDGET_ID = "Memory";
  private static final int MEGABYTE = 1024 * 1024;
  @NonNls private static final String SAMPLE_STRING;
  
  static {
    long maxMemory = Math.min(Runtime.getRuntime().maxMemory() / MEGABYTE, 9999);
    SAMPLE_STRING = maxMemory + " of " + maxMemory + "M ";
  }
  private static final Color USED_COLOR = new JBColor(Gray._185, Gray._110);
  private static final Color UNUSED_COLOR = new JBColor(Gray._200.withAlpha(100), Gray._90);

  private long myLastTotal = -1;
  private long myLastUsed = -1;
  private Image myBufferedImage;
  private boolean myWasPressed;

  public MemoryUsagePanel() {
    setOpaque(false);
    setFocusable(false);

    addActionListener(e -> {
      System.gc();
      updateState();
    });

    setBorder(StatusBarWidget.WidgetBorder.INSTANCE);
    updateUI();

    new UiNotifyConnector(this, new Activatable() {
      private ScheduledFuture<?> myFuture;

      @Override
      public void showNotify() {
        myFuture = EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(MemoryUsagePanel.this::updateState,
                                                                                            1, 5, TimeUnit.SECONDS);
      }

      @Override
      public void hideNotify() {
        if (myFuture != null) {
          myFuture.cancel(true);
          myFuture = null;
        }
      }
    });

  }

  @Override
  public void dispose() {
  }

  @Override
  public void install(@NotNull StatusBar statusBar) { }

  @Override
  @Nullable
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  @Override
  @NotNull
  public String ID() {
    return WIDGET_ID;
  }

  public void setShowing(final boolean showing) {
    if (showing != isVisible()) {
      setVisible(showing);
      revalidate();
    }
  }

  @Override
  public void updateUI() {
    myBufferedImage = null;
    super.updateUI();
    setFont(getWidgetFont());
    setBorder(BorderFactory.createEmptyBorder());
  }

  private static Font getWidgetFont() {
    return JBUI.Fonts.label(11);
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public void paintComponent(final Graphics g) {
    final boolean pressed = getModel().isPressed();
    final boolean stateChanged = myWasPressed != pressed;
    myWasPressed = pressed;

    if (myBufferedImage == null || stateChanged) {
      final Dimension size = getSize();
      final Insets insets = getInsets();

      myBufferedImage = UIUtil.createImage(g, size.width, size.height, BufferedImage.TYPE_INT_ARGB);
      final Graphics2D g2 = (Graphics2D)myBufferedImage.getGraphics().create();

      final Runtime rt = Runtime.getRuntime();
      final long maxMem = rt.maxMemory();
      final long allocatedMem = rt.totalMemory();
      final long unusedMem = rt.freeMemory();
      final long usedMem = allocatedMem - unusedMem;

      final int totalBarLength = size.width - insets.left - insets.right;
      final int usedBarLength = (int)(totalBarLength * usedMem / maxMem);
      final int unusedBarLength = (int)(totalBarLength * unusedMem / maxMem);
      final int barHeight = Math.max(size.height, getFont().getSize() + 2);
      final int yOffset = (size.height - barHeight) / 2;
      final int xOffset = insets.left;

      // background
      g2.setColor(UIUtil.getPanelBackground());
      g2.fillRect(0, 0, size.width, size.height);

      // gauge (used)
      g2.setColor(USED_COLOR);
      g2.fillRect(xOffset, yOffset, usedBarLength, barHeight);

      // gauge (unused)
      g2.setColor(UNUSED_COLOR);
      g2.fillRect(xOffset + usedBarLength, yOffset, unusedBarLength, barHeight);

      // label
      g2.setFont(getFont());
      final long used = usedMem / MEGABYTE;
      final long total = maxMem / MEGABYTE;
      final String info = UIBundle.message("memory.usage.panel.message.text", used, total);
      final FontMetrics fontMetrics = g.getFontMetrics();
      final int infoWidth = fontMetrics.charsWidth(info.toCharArray(), 0, info.length());
      final int infoHeight = fontMetrics.getAscent();
      UISettings.setupAntialiasing(g2);
      final Color fg = pressed ? UIUtil.getLabelDisabledForeground() : JBColor.foreground();
      g2.setColor(fg);
      g2.drawString(info, xOffset + (totalBarLength - infoWidth) / 2, yOffset + infoHeight + (barHeight - infoHeight) / 2 - 1);

      g2.dispose();
    }

    UIUtil.drawImage(g, myBufferedImage, 0, 0, null);
  }

  @Override
  public Dimension getPreferredSize() {
    final Insets insets = getInsets();
    int width = getFontMetrics(getWidgetFont()).stringWidth(SAMPLE_STRING) + insets.left + insets.right + JBUI.scale(2);
    int height = getFontMetrics(getWidgetFont()).getHeight() + insets.top + insets.bottom + JBUI.scale(2);
    return new Dimension(width, height);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  private void updateState() {
    if (!isShowing()) {
      return;
    }

    final Runtime runtime = Runtime.getRuntime();
    final long total = runtime.totalMemory() / MEGABYTE;
    final long used = total - runtime.freeMemory() / MEGABYTE;

    if (total != myLastTotal || used != myLastUsed) {
      myLastTotal = total;
      myLastUsed = used;
      UIUtil.invokeLaterIfNeeded(() -> {
        myBufferedImage = null;
        repaint();
      });

      setToolTipText(UIBundle.message("memory.usage.panel.statistics.message", total, used));
    }
  }
}
