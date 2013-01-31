/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MemoryUsagePanel extends JButton implements CustomStatusBarWidget {
  @NonNls public static final String WIDGET_ID = "Memory";

  // todo: drop unless J. will insist to keep old style look
  private static final boolean FRAMED_STYLE = SystemInfo.isMac || !SystemProperties.getBooleanProperty("idea.ui.old.mem.use", false);

  @NonNls private static final String SAMPLE_STRING = "0000M of 0000M";
  private static final int MEGABYTE = 1024 * 1024;
  private static final int HEIGHT = 16;
  private static final Color USED_COLOR_1 = new JBColor(Gray._185, Gray._150);
  private static final Color USED_COLOR_2 = new JBColor(Gray._145, Gray._120);
  private static final Color UNUSED_COLOR_1 = new JBColor(Gray._200.withAlpha(100), Gray._120);
  private static final Color UNUSED_COLOR_2 = new JBColor(Gray._150.withAlpha(130), Gray._100);
  private static final Color UNUSED_COLOR_3 = Gray._175;

  private long myLastTotal = -1;
  private long myLastUsed = -1;
  private ScheduledFuture<?> myFuture;
  private Image myBufferedImage;
  private boolean myWasPressed;

  public MemoryUsagePanel() {
    setOpaque(false);
    setFocusable(false);

    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        System.gc();
        updateState();
      }
    });

    setBorder(StatusBarWidget.WidgetBorder.INSTANCE);
    updateUI();
  }

  @Override
  public void dispose() {
    myFuture.cancel(true);
    myFuture = null;
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
    super.updateUI();
    setFont(getWidgetFont());
  }

  private static Font getWidgetFont() {
    final Font font = UIUtil.getLabelFont();
    return FRAMED_STYLE ? font.deriveFont(11.0f) : font;
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
      final Insets insets = FRAMED_STYLE ? getInsets() : new Insets(0, 0, 0, 0);

      myBufferedImage = UIUtil.createImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
      final Graphics2D g2 = (Graphics2D)myBufferedImage.getGraphics().create();

      final Runtime rt = Runtime.getRuntime();
      final long maxMem = rt.maxMemory();
      final long allocatedMem = rt.totalMemory();
      final long unusedMem = rt.freeMemory();
      final long usedMem = allocatedMem - unusedMem;

      final int totalBarLength = size.width - insets.left - insets.right;
      final int usedBarLength = (int)(totalBarLength * usedMem / maxMem);
      final int unusedBarLength = (int)(totalBarLength * unusedMem / maxMem);
      final int barHeight = FRAMED_STYLE ? HEIGHT : size.height;
      final int yOffset = (size.height - barHeight) / 2;
      final int xOffset = insets.left;

      // background
      if (!UIUtil.isUnderAquaLookAndFeel()) {
        g2.setColor(UIUtil.getControlColor());
      }
      else {
        g2.setPaint(UIUtil.getGradientPaint(0, 0, new JBColor(Gray._190, Gray._120), 0, barHeight, new JBColor(Gray._230, Gray._160)));
      }
      g2.fillRect(xOffset, yOffset, totalBarLength, barHeight);

      // gauge (used)
      setGradient(g2, pressed, barHeight, USED_COLOR_1, USED_COLOR_2);
      g2.fillRect(xOffset, yOffset, usedBarLength, barHeight);

      // gauge (unused)
      setGradient(g2, pressed, barHeight, UNUSED_COLOR_1, UNUSED_COLOR_2);
      g2.fillRect(xOffset + usedBarLength, yOffset, unusedBarLength, barHeight);
      if (!UIUtil.isUnderDarcula()) {
        g2.setColor(UNUSED_COLOR_3);
        g2.drawLine(xOffset + usedBarLength + unusedBarLength, yOffset, xOffset + usedBarLength + unusedBarLength, barHeight);
      }

      // frame
      if (FRAMED_STYLE && !UIUtil.isUnderDarcula()) {
        g2.setColor(USED_COLOR_2);
        g2.drawRect(xOffset, yOffset, totalBarLength - 1, barHeight - 1);
      }

      // label
      g2.setFont(getFont());
      final long used = usedMem / MEGABYTE;
      final long total = maxMem / MEGABYTE;
      final String info = UIBundle.message("memory.usage.panel.message.text", used, total);
      final FontMetrics fontMetrics = g.getFontMetrics();
      final int infoWidth = fontMetrics.charsWidth(info.toCharArray(), 0, info.length());
      final int infoHeight = fontMetrics.getAscent();
      UIUtil.applyRenderingHints(g2);
      g2.setColor(UIUtil.getLabelForeground());
      g2.drawString(info, xOffset + (totalBarLength - infoWidth) / 2, yOffset + infoHeight + (barHeight - infoHeight) / 2 - 1);

      g2.dispose();
    }

    g.drawImage(myBufferedImage, 0, 0, null);
  }

  private static void setGradient(Graphics2D g2, boolean invert, int height, Color start, Color end) {
    if (UIUtil.isUnderDarcula()) {
      start = start.darker();
      end = end.darker();
    }
    if (invert) {
      g2.setPaint(UIUtil.getGradientPaint(0, 0, end, 0, height, start));
    }
    else {
      g2.setPaint(UIUtil.getGradientPaint(0, 0, start, 0, height, end));
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(getPreferredWidth(),
                         isVisible() && getParent() != null ? getParent().getSize().height : super.getPreferredSize().height);
  }

  private int getPreferredWidth() {
    final Insets insets = getInsets();
    return getFontMetrics(getWidgetFont()).stringWidth(SAMPLE_STRING) + insets.left + insets.right + 2;
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  @Override
  public void addNotify() {
    myFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(new Runnable() {
      public void run() {
        if (isDisplayable()) {
          updateState();
        }
      }
    }, 1, 5, TimeUnit.SECONDS);
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
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myBufferedImage = null;
          repaint();
        }
      });

      setToolTipText(UIBundle.message("memory.usage.panel.statistics.message", total, used));
    }
  }
}
