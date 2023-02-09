// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.wm.CustomStatusBarWidget;
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

public final class MemoryUsagePanel extends TextPanel implements CustomStatusBarWidget, Activatable {
  public static final String WIDGET_ID = "Memory";

  private final Color myUsedColor = JBColor.namedColor("MemoryIndicator.usedBackground", new JBColor(Gray._185, Gray._110));
  private final Color myUnusedColor = JBColor.namedColor("MemoryIndicator.allocatedBackground", new JBColor(Gray._215, Gray._90));
  private final long myMaxMemory = Math.min(Runtime.getRuntime().maxMemory() >> 20, 9999);
  private long myLastAllocated = -1;
  private long myLastUsed = -1;
  private ScheduledFuture<?> myFuture;

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
  public @Nullable WidgetPresentation getPresentation() {
    return null;
  }

  @Override
  public @NotNull String ID() {
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
    g.fillRect(0, 0, barWidth, size.height);

    // gauge (allocated)
    g.setColor(myUnusedColor);
    g.fillRect(0, 0, allocatedBarLength, size.height);

    // gauge (used)
    g.setColor(myUsedColor);
    g.fillRect(0, 0, usedBarLength, size.height);

    //text
    super.paintComponent(g);
  }

  @Override
  protected String getTextForPreferredSize() {
    var sample = myMaxMemory < 1000 ? 999 : myMaxMemory < 10000 ? 9999 : 99999;
    return " " + UIBundle.message("memory.usage.panel.message.text", sample, sample);
  }

  private void updateState() {
    if (!isShowing()) {
      return;
    }

    var rt = Runtime.getRuntime();
    var maxMem = rt.maxMemory() >> 20;
    var allocatedMem = rt.totalMemory() >> 20;
    var usedMem = allocatedMem - (rt.freeMemory() >> 20);
    var text = UIBundle.message("memory.usage.panel.message.text", usedMem, maxMem);

    if (allocatedMem != myLastAllocated || usedMem != myLastUsed || !text.equals(getText())) {
      myLastAllocated = allocatedMem;
      myLastUsed = usedMem;
      setText(text);
      setToolTipText(UIBundle.message("memory.usage.panel.message.tooltip", maxMem, allocatedMem, usedMem));
    }
  }
}
