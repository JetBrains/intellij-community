// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.ClickListener;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.util.io.FileUtilRt.MEGABYTE;

public final class MemoryUsagePanel extends TextPanel implements CustomStatusBarWidget, Activatable {
  public static final String WIDGET_ID = "Memory";

  private static final Color USED_COLOR = JBColor.namedColor("MemoryIndicator.usedBackground", new JBColor(Gray._185, Gray._110));
  private static final Color UNUSED_COLOR = JBColor.namedColor("MemoryIndicator.allocatedBackground", new JBColor(Gray._215, Gray._90));

  private long myLastAllocated = -1;
  private long myLastUsed = -1;
  private ScheduledFuture<?> myFuture;
  private final long myMaxMemory = Math.min(Runtime.getRuntime().maxMemory() / MEGABYTE, 9999);
  private StatusBar myStatusBar;

  public MemoryUsagePanel() {
    setFocusable(false);
    setTextAlignment(CENTER_ALIGNMENT);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        //noinspection CallToSystemGC
        System.gc();
        updateState();
        return true;
      }
    }.installOn(this, true);
    setBorder(JBUI.Borders.empty(0, 2));
    updateUI();

    new UiNotifyConnector(this, this);
  }

  @Override
  public Color getBackground() {
    return null;
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
  public void install(@NotNull StatusBar statusBar) {
    if (statusBar instanceof IdeStatusBarImpl) {
      ((IdeStatusBarImpl)statusBar).setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 6));
    }
  }

  @Override
  public void dispose() { 
    if (myStatusBar instanceof IdeStatusBarImpl) {
      ((IdeStatusBarImpl)myStatusBar).setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
    }
    myStatusBar = null;
  }

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
    int barWidth = size.width;

    Runtime rt = Runtime.getRuntime();
    long maxMem = rt.maxMemory();
    long allocatedMem = rt.totalMemory();
    long unusedMem = rt.freeMemory();
    long usedMem = allocatedMem - unusedMem;

    int usedBarLength = (int)(barWidth * usedMem / maxMem);
    int allocatedBarLength = (int)(barWidth * allocatedMem / maxMem);

    // background
    g.setColor(UIUtil.getPanelBackground());
    g.fillRect(0, 0, barWidth, size.height - 1);

    // gauge (allocated)
    g.setColor(UNUSED_COLOR);
    g.fillRect(0, 0, allocatedBarLength, size.height - 1);

    // gauge (used)
    g.setColor(USED_COLOR);
    g.fillRect(0, 0, usedBarLength, size.height - 1);

    //text
    super.paintComponent(g);
  }

  @Override
  protected String getTextForPreferredSize() {
    long sample = myMaxMemory < 1000 ? 999 : 9999;
    return " " + UIBundle.message("memory.usage.panel.message.text", sample, sample);
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