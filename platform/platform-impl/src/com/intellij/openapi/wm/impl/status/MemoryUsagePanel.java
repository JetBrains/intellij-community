// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

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

  public static final String SHOW_TOTAL_MEMORY_ESTIMATION_REGISTRY_KEY = "idea.memory.usage.show.total.memory.estimation";
  public static final String SHOW_MORE_INFO_IN_TOOLTIP_REGISTRY_KEY = "idea.memory.usage.tooltip.show.more";

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

    private long lastCommitedMb = -1;
    private long lastUsedMb = -1;

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


      long usedMem;
      long allocatedMem;
      long maxMem;
      if (Registry.is(SHOW_TOTAL_MEMORY_ESTIMATION_REGISTRY_KEY)) {
        // [ heap used | heap commited | total (approx.) commited ]
        AppMemoryUsage memoryUsage = calculateMemoryUsage();
        maxMem = toMb(memoryUsage.estimatedTotalMemoryUsedBytes());
        allocatedMem = toMb(memoryUsage.heapCommitedBytes);
        usedMem = toMb(memoryUsage.heapUsedBytes);
      }
      else {
        // [ heap used | heap commited | heap max ]
        // use old-school heap accessor:
        Runtime runtime = Runtime.getRuntime();

        maxMem = runtime.maxMemory();
        allocatedMem = runtime.totalMemory();
        usedMem = allocatedMem - runtime.freeMemory();
      }

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
      long maxMemoryMb = toMb(Runtime.getRuntime().maxMemory());
      long sample = maxMemoryMb < 1000 ? 999 :
                    maxMemoryMb < 10_000 ? 9_999 : 99_999;
      //if -Xmx > 100Gb -- well, I'm sorry
      return " " + UIBundle.message("memory.usage.panel.message.text", sample, sample);
    }

    private void updateState() {
      if (!isShowing()) {
        return;
      }
      AppMemoryUsage memoryUsage = calculateMemoryUsage();

      // convert to UI-friendly Mb:
      long heapMaxMb = toMb(memoryUsage.heapMaxBytes);
      long heapCommitedMb = toMb(memoryUsage.heapCommitedBytes);
      long heapUsedMb = toMb(memoryUsage.heapUsedBytes);

      long directBuffersUsedMb = toMb(memoryUsage.directByteBuffersBytes);
      long directBuffersFileCacheUsedMb = toMb(memoryUsage.directBuffersFileCacheUsedBytes);

      long jvmInternalsMb = toMb(memoryUsage.jvmInternalsMemoryBytes);
      long threadStacksMemoryMb = toMb(memoryUsage.threadStacksBytes);

      long memoryMappedFilesMb = toMb(memoryUsage.memoryMappedFilesBytes);

      long estimatedTotalMemoryUsedMb = toMb(memoryUsage.estimatedTotalMemoryUsedBytes());

      var text = Registry.is(SHOW_TOTAL_MEMORY_ESTIMATION_REGISTRY_KEY) ?
                 UIBundle.message("memory.usage.panel.message.text", heapUsedMb, estimatedTotalMemoryUsedMb) :
                 UIBundle.message("memory.usage.panel.message.text", heapUsedMb, heapMaxMb);

      if (heapCommitedMb != lastCommitedMb || heapUsedMb != lastUsedMb || !text.equals(getText())) {
        lastCommitedMb = heapCommitedMb;
        lastUsedMb = heapUsedMb;
        setText(text);

        boolean showExtendedInfoInTooTip = Registry.is(SHOW_MORE_INFO_IN_TOOLTIP_REGISTRY_KEY);
        String i18nBundleKey = showExtendedInfoInTooTip ?
                               "memory.usage.panel.message.tooltip-extended" :
                               "memory.usage.panel.message.tooltip";
        setToolTipText(
          UIBundle.message(i18nBundleKey,

                           heapUsedMb, heapCommitedMb, heapMaxMb,

                           directBuffersFileCacheUsedMb, (directBuffersUsedMb - directBuffersFileCacheUsedMb),

                           jvmInternalsMb, threadStacksMemoryMb,

                           estimatedTotalMemoryUsedMb,

                           memoryMappedFilesMb //shown only in .tooltip-extended version
          ));
      }
    }
  }


  private static final MemoryMXBean MEMORY_MX_BEAN = ManagementFactory.getMemoryMXBean();
  private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();

  private static AppMemoryUsage calculateMemoryUsage() {
    MemoryUsage heapMemoryUsage = MEMORY_MX_BEAN.getHeapMemoryUsage();

    long directBuffersUsedByFileCacheBytes = DirectByteBufferAllocator.ALLOCATOR.getStatistics().totalSizeOfBuffersAllocatedInBytes;
    //RC: counter-intuitively, but memoryMXBean.getNonHeapMemoryUsage() does NOT count direct ByteBuffers.
    //    nonHeapMemoryUsage is mostly about JVM-internal data structures -- code cache, metaspace, etc.
    //    Direct ByteBuffers (seems to be) invisible to any public API, so we need some private API for it
    long directBuffersUsedBytes = IOUtil.directBuffersTotalAllocatedSize();
    if (directBuffersUsedBytes <= 0) {
      //can't get value by some reason => use directBuffersUsedByFileCacheBytes as lower bound, better than nothing:
      directBuffersUsedBytes = directBuffersUsedByFileCacheBytes;
    }

    //RC: I know no way to get thread-stack size, but 1Mb seems to be a default stack size for most OSes, so
    //    lets just assume (1 thread = 1Mb of stack). This seems to be an underestimation: seems like JVM
    //    provision memory for threads with big margin, and also thread local allocation 'arenas' are not included
    long threadsStackBytes = THREAD_MX_BEAN.getThreadCount() * (long)IOUtil.MiB;

    //pools list could change during execution, so can't be cached once
    long jvmInternalsMemoryBytes = jvmInternalsMemory(ManagementFactory.getMemoryPoolMXBeans());

    long memoryMappedFilesBytes = MMappedFileStorage.totalBytesMapped();

    return new AppMemoryUsage(
      heapMemoryUsage.getMax(), heapMemoryUsage.getCommitted(), heapMemoryUsage.getUsed(),
      jvmInternalsMemoryBytes,
      directBuffersUsedBytes, directBuffersUsedByFileCacheBytes,
      threadsStackBytes,
      memoryMappedFilesBytes
    );
  }

  private static final class AppMemoryUsage {
    public final long heapMaxBytes;
    public final long heapCommitedBytes;
    public final long heapUsedBytes;

    public final long jvmInternalsMemoryBytes;
    public final long directByteBuffersBytes;
    public final long directBuffersFileCacheUsedBytes;

    public final long threadStacksBytes;

    public final long memoryMappedFilesBytes;

    private AppMemoryUsage(long heapMaxBytes,
                           long heapCommitedBytes,
                           long heapUsedBytes,
                           long jvmInternalsMemoryBytes,
                           long directByteBuffersBytes,
                           long directBuffersFileCacheUsedBytes,
                           long threadStacksBytes,
                           long memoryMappedFilesBytes) {
      this.heapMaxBytes = heapMaxBytes;
      this.heapCommitedBytes = heapCommitedBytes;
      this.heapUsedBytes = heapUsedBytes;
      this.jvmInternalsMemoryBytes = jvmInternalsMemoryBytes;
      this.directByteBuffersBytes = directByteBuffersBytes;
      this.directBuffersFileCacheUsedBytes = directBuffersFileCacheUsedBytes;
      this.threadStacksBytes = threadStacksBytes;
      this.memoryMappedFilesBytes = memoryMappedFilesBytes;
    }

    public long estimatedTotalMemoryUsedBytes() {
      //Should be +/- good estimation:
      return roundUpTo(
        heapCommitedBytes + threadStacksBytes + directByteBuffersBytes + jvmInternalsMemoryBytes,
        100 * IOUtil.MiB //to show too many digits could be confusing for a 'rough estimation'
      );
    }
  }

  private static long jvmInternalsMemory(@NotNull List<MemoryPoolMXBean> memoryPools) {
    return memoryPools.stream()
      .filter(pool -> pool.getType() == MemoryType.NON_HEAP)
      .mapToLong(pool -> pool.getUsage().getUsed())
      .sum();
  }

  /** @return value rounded up the nearest bucket up */
  private static long roundUpTo(long value,
                                long bucket) {
    long fraction = value / bucket;
    long remainder = value % bucket;
    if (remainder > 0) {
      return (fraction + 1) * bucket;
    }
    else {
      return value;
    }
  }

  private static long toMb(long value) {
    return value / IOUtil.MiB;
  }
}
