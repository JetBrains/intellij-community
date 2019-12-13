// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.util.io.FileUtilRt.MEGABYTE;

public final class MemoryUsagePanel extends TextPanel implements CustomStatusBarWidget, Activatable {
  public static final String WIDGET_ID = "Memory";

  private static final int INDENT = 6;
  private static final Color USED_COLOR = JBColor.namedColor("MemoryIndicator.usedBackground", new JBColor(Gray._185, Gray._110));
  private static final Color UNUSED_COLOR = JBColor.namedColor("MemoryIndicator.allocatedBackground", new JBColor(Gray._215, Gray._90));

  private final String mySample;
  private long myLastAllocated = -1;
  private long myLastUsed = -1;
  private ScheduledFuture<?> myFuture;

  public MemoryUsagePanel() {
    long max = Math.min(Runtime.getRuntime().maxMemory() / MEGABYTE, 9999);
    mySample = UIBundle.message("memory.usage.panel.message.text", max, max);

    setOpaque(false);
    setFocusable(false);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        //noinspection CallToSystemGC
        System.gc();
        updateState();
      }
    });
    setBorder(JBUI.Borders.empty(0, 2));
    updateUI();

    new UiNotifyConnector(this, this);
  }

  @Override
  public void showNotify() {
    myFuture = EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(this::updateState, 1, 5, TimeUnit.SECONDS);
  }

  @Override
  public void hideNotify() {
    if (myFuture != null) {
      myFuture.cancel(true);
      myFuture = null;
    }
  }

  @Override
  public void dispose() { }

  @Override
  public void install(@NotNull StatusBar statusBar) { }

  @Nullable
  @Override
  public WidgetPresentation getPresentation() {
    return null;
  }

  @NotNull
  @Override
  public String ID() {
    return WIDGET_ID;
  }

  public void setShowing(boolean showing) {
    if (showing != isVisible()) {
      setVisible(showing);
      revalidate();
    }
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public void paintComponent(Graphics g) {
    Dimension size = getSize();
    int barWidth = size.width - INDENT;

    Runtime rt = Runtime.getRuntime();
    long maxMem = rt.maxMemory();
    long allocatedMem = rt.totalMemory();
    long unusedMem = rt.freeMemory();
    long usedMem = allocatedMem - unusedMem;

    int usedBarLength = (int)(barWidth * usedMem / maxMem);
    int unusedBarLength = (int)(size.height * unusedMem / maxMem);

    // background
    g.setColor(UIUtil.getPanelBackground());
    g.fillRect(0, 0, barWidth, size.height);

    // gauge (used)
    g.setColor(USED_COLOR);
    g.fillRect(0, 0, usedBarLength, size.height);

    // gauge (unused)
    g.setColor(UNUSED_COLOR);
    g.fillRect(usedBarLength, 0, unusedBarLength, size.height);

    //text
    super.paintComponent(g);
  }

  @Override
  public Dimension getPreferredSize() {
    FontMetrics metrics = getFontMetrics(getFont());
    Insets insets = getInsets();
    int width = metrics.stringWidth(mySample) + insets.left + insets.right + JBUIScale.scale(2) + INDENT;
    int height = metrics.getHeight() + insets.top + insets.bottom + JBUIScale.scale(2);
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

    Runtime rt = Runtime.getRuntime();
    long maxMem = rt.maxMemory() / MEGABYTE;
    long allocatedMem = rt.totalMemory() / MEGABYTE;
    long usedMem = allocatedMem - rt.freeMemory() / MEGABYTE;
    String text = UIBundle.message("memory.usage.panel.message.text", usedMem, maxMem);

    if (allocatedMem != myLastAllocated || usedMem != myLastUsed || !StringUtil.equals(text, getText())) {
      myLastAllocated = allocatedMem;
      myLastUsed = usedMem;
      setText(text);
      setToolTipText(UIBundle.message("memory.usage.panel.statistics.message", maxMem, allocatedMem, usedMem));
    }
  }
}