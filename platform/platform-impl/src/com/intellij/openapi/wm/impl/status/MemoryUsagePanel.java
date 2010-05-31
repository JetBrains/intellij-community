/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl.status;

import com.intellij.concurrency.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.*;
import com.intellij.ui.*;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.*;

public class MemoryUsagePanel extends JButton implements CustomStatusBarWidget {
  @NonNls
  private static final String SAMPLE_STRING = "0000M of 0000M";
  private static final int MEGABYTE = 1024 * 1024;
  private static final Color ourColorFree = new Color(240, 240, 240);
  private static final Color ourColorUsed = new Color(112, 135, 214);
  private static final Color ourColorUsed2 = new Color(166, 181, 230);

  private static final int HEIGHT = 16;

  private long myLastTotal = -1;
  private long myLastUsed = -1;
  private ScheduledFuture<?> myFuture;

  public MemoryUsagePanel() {
    setOpaque(false);

    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        System.gc();
        updateState();
      }
    });

    setBorder(StatusBarWidget.WidgetBorder.INSTANCE);
    updateUI();
  }

  public void dispose() {
    myFuture.cancel(true);
    myFuture = null;
  }

  public void install(@NotNull StatusBar statusBar) {
  }

  @Nullable
  public Presentation getPresentation(@NotNull Type type) {
    return null;
  }

  @NotNull
  public String ID() {
    return "Memory";
  }

  public void setShowing(final boolean showing) {
    if (showing && !isVisible()) {
      setVisible(true);
      revalidate();
    } else if (!showing && isVisible()) {
      setVisible(showing);
      revalidate();
    }
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setFont(SystemInfo.isMac ? UIUtil.getLabelFont().deriveFont(11.0f) : UIUtil.getLabelFont());
  }

  public JComponent getComponent() {
    return this;
  }

  @Override
  public void paintComponent(final Graphics g) {
    final Dimension size = getSize();

    final Runtime runtime = Runtime.getRuntime();
    final long maxMemory = runtime.maxMemory();
    final long freeMemory = maxMemory - runtime.totalMemory() + runtime.freeMemory();

    final Insets insets = getInsets();

    final int totalBarLength = size.width - insets.left - insets.right - (SystemInfo.isMac ? 0 : 0);
    final int usedBarLength = totalBarLength - (int)(totalBarLength * freeMemory / maxMemory);
    final int barHeight = HEIGHT; // size.height - insets.top - insets.bottom;
    final Graphics2D g2 = (Graphics2D)g;

    final int yOffset = (size.height - barHeight) / 2;
    final int xOffset = insets.left + (SystemInfo.isMac ? 0 : 0);

    final boolean pressed = getModel().isPressed();

    g2.setPaint(new GradientPaint(0, 0, new Color(190, 190, 190), 0, size.height - 1, new Color(230, 230, 230)));
    g2.fillRect(xOffset, yOffset, totalBarLength, barHeight);

    if (pressed) {
      g2.setPaint(new GradientPaint(1, 1, new Color(101, 111, 135), 0, size.height - 2, new Color(175, 185, 202)));
      g2.fillRect(xOffset + 1, yOffset, usedBarLength, barHeight);
    } else {
      g2.setPaint(new GradientPaint(1, 1, new Color(175, 185, 202), 0, size.height - 2, new Color(126, 138, 168)));
      g2.fillRect(xOffset + 1, yOffset, usedBarLength, barHeight);

      g2.setColor(new Color(194, 197, 203));
      g2.drawLine(xOffset + 1, yOffset+1, xOffset + usedBarLength, yOffset+1);
    }

    g2.setColor(new Color(110, 110, 110));
    g2.drawRect(xOffset, yOffset, totalBarLength, barHeight - 1);

    g.setFont(getFont());
    final long used = (maxMemory - freeMemory) / MEGABYTE;
    final long total = maxMemory / MEGABYTE;
    final String info = UIBundle.message("memory.usage.panel.message.text", Long.toString(used), Long.toString(total));
    final FontMetrics fontMetrics = g.getFontMetrics();
    final int infoWidth = fontMetrics.charsWidth(info.toCharArray(), 0, info.length());
    final int infoHeight = fontMetrics.getHeight() - fontMetrics.getDescent();
    UIUtil.applyRenderingHints(g);

    g.setColor(Color.black);
    g.drawString(info, xOffset + (totalBarLength - infoWidth) / 2, yOffset + (barHeight + infoHeight) / 2 - 1);
  }

  public final int getPreferredWidth() {
    final Insets insets = getInsets();
    return getFontMetrics(UIUtil.getLabelFont().deriveFont(11.0f)).stringWidth(SAMPLE_STRING) + insets.left + insets.right + (SystemInfo.isMac ? 2 : 0);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(getPreferredWidth(), Integer.MAX_VALUE);
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  public void addNotify() {
    myFuture = JobScheduler.getScheduler().scheduleAtFixedRate(new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (!isDisplayable()) return; // This runnable may be posted in event queue while calling removeNotify.
            updateState();
          }
        });
      }
    }, 1, 1, TimeUnit.SECONDS);
    super.addNotify();
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

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          repaint();
        }
      });

      setToolTipText(UIBundle.message("memory.usage.panel.statistics.message", total, used));
    }
  }
}
