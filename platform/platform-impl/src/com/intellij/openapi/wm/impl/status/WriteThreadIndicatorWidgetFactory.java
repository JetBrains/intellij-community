// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Deque;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class WriteThreadIndicatorWidgetFactory implements StatusBarWidgetFactory {
  private static final String ID = "WriteThread";

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return UIBundle.message("status.bar.write.thread.widget.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    return ApplicationManager.getApplication().isInternal();
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new WriteThreadWidget();
  }

  @Override
  public void disposeWidget(@NotNull StatusBarWidget widget) {
    Disposer.dispose(widget);
  }

  @Override
  public boolean isConfigurable() {
    return ApplicationManager.getApplication().isInternal();
  }

  @Override
  public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
    return ApplicationManager.getApplication().isInternal();
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  private static class WriteThreadWidget implements CustomStatusBarWidget {
    private static final Dimension WIDGET_SIZE = new Dimension(100, 20);
    private final JPanel myComponent = new MyComponent();
    private final Deque<AtomicIntegerArray> myStatsDeque = new LinkedBlockingDeque<>();
    private volatile AtomicIntegerArray myCurrentStats = new AtomicIntegerArray(4);

    private final Timer myTimer = new Timer(500, e -> {
      myStatsDeque.add(myCurrentStats);
      while (myStatsDeque.size() > WIDGET_SIZE.width) {
        myStatsDeque.pop();
      }
      myCurrentStats = new AtomicIntegerArray(4);
      myComponent.repaint();
    });
    private final java.util.Timer ourTimer2 = new java.util.Timer("Write Thread Widget Timer");


    @Override
    public JComponent getComponent() {
      return myComponent;
    }

    @NotNull
    @Override
    public String ID() {
      return ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
      ApplicationImpl application = ObjectUtils.tryCast(ApplicationManager.getApplication(), ApplicationImpl.class);
      if (application == null) {
        return;
      }

      ourTimer2.scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
          ThreeState currentValue = application.isCurrentWriteOnEdt();
          AtomicIntegerArray currentStats = myCurrentStats;
          currentStats.incrementAndGet(currentValue.ordinal());
          currentStats.incrementAndGet(3);
        }
      }, 0, 1);
      myTimer.start();
    }

    @Override
    public void dispose() {
      ourTimer2.cancel();
      myTimer.stop();
    }

    private class MyComponent extends JPanel {
      @Override
      public Dimension getPreferredSize() {
        return WIDGET_SIZE;
      }

      @Override
      public Dimension getMinimumSize() {
        return WIDGET_SIZE;
      }

      @Override
      public Dimension getMaximumSize() {
        return WIDGET_SIZE;
      }

      @Override
      public void paint(Graphics g) {
        super.paint(g);
        if (g instanceof Graphics2D) {
          Graphics2D g2d = (Graphics2D)g;

          int offsetx = 0;
          for (AtomicIntegerArray stats : myStatsDeque) {
            g2d.setColor(JBColor.GRAY);
            g2d.fillRect(offsetx, 0, 1, WIDGET_SIZE.height);

            int sum = stats.get(3);
            int offsety = 0;
            int height;

            if (sum > 0) {
              g2d.setColor(JBColor.RED);
              height = (stats.get(0) * WIDGET_SIZE.height + sum - 1) / sum;
              g2d.fillRect(offsetx, WIDGET_SIZE.height - offsety - height, 1, height);
              offsety -= height;

              g2d.setColor(JBColor.GREEN);
              height = (stats.get(1) * WIDGET_SIZE.height + sum - 1) / sum;
              g2d.fillRect(offsetx, WIDGET_SIZE.height - offsety - height, 1, height);
            }

            offsetx++;
          }
        }
      }
    }
  }
}
