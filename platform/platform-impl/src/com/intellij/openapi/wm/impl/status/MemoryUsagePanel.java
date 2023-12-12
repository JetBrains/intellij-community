// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.ui.ClickListener;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.util.LazyInitializer;
import com.intellij.util.LazyInitializer.LazyValue;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.io.DirectByteBufferAllocator;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.dev.mmapped.MMappedFileStorage;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.management.*;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class MemoryUsagePanel implements CustomStatusBarWidget, Activatable {
  public static final String WIDGET_ID = "Memory";

  private final LazyValue<MemoryUsagePanelImpl> myComponent = LazyInitializer.create(MemoryUsagePanelImpl::new);
  private ScheduledFuture<?> myFuture;

  @Override
  public void showNotify() {
    myFuture = EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(
      () -> myComponent.get().updateState(), 1, 5, TimeUnit.SECONDS
    );
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

  @Override
  public JComponent getComponent() {
    return myComponent.get();
  }

  public static boolean isInstance(@NotNull JComponent component) {
    return component instanceof MemoryUsagePanelImpl;
  }

  // These three methods are purely for internal ABI compatibility, as some plugins use them.

  public void addMouseListener(MouseListener l) {
    myComponent.get().addMouseListener(l);
  }

  public MouseListener[] getMouseListeners() {
    return myComponent.get().getMouseListeners();
  }

  public void removeMouseListener(MouseListener l) {
    myComponent.get().removeMouseListener(l);
  }

  private final class MemoryUsagePanelImpl extends TextPanel {

    private final Color myUsedColor = JBColor.namedColor("MemoryIndicator.usedBackground", new JBColor(Gray._185, Gray._110));
    private final Color myUnusedColor = JBColor.namedColor("MemoryIndicator.allocatedBackground", new JBColor(Gray._215, Gray._90));
    private final long myMaxMemory = Math.min(Runtime.getRuntime().maxMemory() >> 20, 9999);

    private long myLastCommitedMb = -1;
    private long myLastUsedMb = -1;

    MemoryUsagePanelImpl() {
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

      UiNotifyConnector.installOn(this, MemoryUsagePanel.this);
    }

    @Override
    public Color getBackground() {
      return null;
    }

    public void setShowing(boolean showing) {
      if (showing != isVisible()) {
        setVisible(showing);
        revalidate();
      }
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

      //TODO RC: cache MX-beans in a fields?
      var memoryMXBean = ManagementFactory.getMemoryMXBean();
      var threadMXBean = ManagementFactory.getThreadMXBean();
      List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();

      MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
      long heapMaxBytes = heapMemoryUsage.getMax();
      long heapCommitedBytes = heapMemoryUsage.getCommitted();
      long heapUsedBytes = heapMemoryUsage.getUsed();

      long directBuffersUsedBytes = IOUtil.directBuffersTotalAllocatedSize();
      long directBuffersUsedByFileCacheBytes = DirectByteBufferAllocator.ALLOCATOR.getStatistics().totalSizeOfBuffersAllocatedInBytes;
      if (directBuffersUsedBytes <= 0) {
        //can't get value by some reason => use directBuffersUsedByFileCacheBytes as lower bound, better than nothing:
        directBuffersUsedBytes = directBuffersUsedByFileCacheBytes;
      }

      long totalMemoryMappedBytes = MMappedFileStorage.totalBytesMapped();

      // convert to UI-friendly Mb:
      long heapMaxMb = heapMaxBytes / IOUtil.MiB;
      long heapCommitedMb = heapCommitedBytes / IOUtil.MiB;
      long heapUsedMb = heapUsedBytes / IOUtil.MiB;

      long directBuffersUsedMb = directBuffersUsedBytes / IOUtil.MiB;
      long directBuffersFileCacheUsedMb = directBuffersUsedByFileCacheBytes / IOUtil.MiB;

      long jvmInternalsMb = jvmInternalsMemory(memoryPoolMXBeans) / IOUtil.MiB;
      //RC: I know no way to get thread-stack size, but 1Mb seems to be a default stack size for most OSes, so
      //    lets just assume (1 thread = 1Mb of stack). This seems to be an underestimation: seems like JVM
      //    provision memory for threads with big margin, and also thread local allocation 'arenas' are not included
      long threadStacksMemoryMb = threadMXBean.getThreadCount();

      long totalMemoryMappedMb = totalMemoryMappedBytes / IOUtil.MiB;

      //Should be +/- good estimation:
      long estimatedTotalMemoryUsedMb = heapCommitedMb + threadStacksMemoryMb + directBuffersUsedMb + jvmInternalsMb;

      var text = UIBundle.message("memory.usage.panel.message.text", heapUsedMb, heapMaxMb);

      if (heapCommitedMb != myLastCommitedMb || heapUsedMb != myLastUsedMb || !text.equals(getText())) {
        myLastCommitedMb = heapCommitedMb;
        myLastUsedMb = heapUsedMb;
        setText(text);

        boolean showExtendedInfoInTooTip = Registry.is("idea.memory.usage.tooltip.show.more",
                                                       ApplicationManager.getApplication().isInternal());
        String i18nBundleKey = showExtendedInfoInTooTip ?
                               "memory.usage.panel.message.tooltip-extended" :
                               "memory.usage.panel.message.tooltip";
        setToolTipText(
          UIBundle.message(i18nBundleKey,

                           heapUsedMb, heapCommitedMb, heapMaxMb,

                           directBuffersFileCacheUsedMb, (directBuffersUsedMb - directBuffersFileCacheUsedMb),

                           jvmInternalsMb, threadStacksMemoryMb,

                           estimatedTotalMemoryUsedMb,

                           totalMemoryMappedMb //shown only in .tooltip-extended version
          ));
      }
    }

    private static long jvmInternalsMemory(@NotNull List<MemoryPoolMXBean> memoryPools) {
      return memoryPools.stream()
        .filter(pool -> pool.getType() == MemoryType.NON_HEAP)
        .mapToLong(pool -> pool.getUsage().getUsed())
        .sum();
    }
  }
}
