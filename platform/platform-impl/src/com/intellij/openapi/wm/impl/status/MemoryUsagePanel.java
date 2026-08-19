// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.diagnostic.PlatformMemoryUtil;
import com.intellij.ide.HelpTooltipKt;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import com.intellij.ui.ClickListener;
import com.intellij.ui.Gray;
import com.intellij.ui.IslandsState;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.util.LazyInitializer;
import com.intellij.util.LazyInitializer.LazyValue;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.io.DirectByteBufferAllocator;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.jetbrains.JBR;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.List;

@ApiStatus.Internal
public final class MemoryUsagePanel implements CustomStatusBarWidget, Activatable {
  public static final String WIDGET_ID = "Memory";

  public static final String SHOW_TOTAL_MEMORY_ESTIMATION_REGISTRY_KEY = "idea.memory.usage.show.total.memory.estimation";

  private final LazyValue<MemoryUsagePanelImpl> myComponent = LazyInitializer.create(MemoryUsagePanelImpl::new);

  private final MemoryUsagePanelScheduler scheduler = new MemoryUsagePanelScheduler((data -> {
    myComponent.get().updateState(data.getAppMemory(), data.getRuntimeMemory(), data.getProcessMemoryStats());
  }));

  @Override
  public void showNotify() {
    scheduler.start();
  }

  @Override
  public void hideNotify() {
    scheduler.stop();
  }

  @Override
  public void dispose() {
    scheduler.dispose();
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
    getComponent().addMouseListener(l);
  }

  public MouseListener[] getMouseListeners() {
    return getComponent().getMouseListeners();
  }

  public void removeMouseListener(MouseListener l) {
    getComponent().removeMouseListener(l);
  }

  private final class MemoryUsagePanelImpl extends TextPanel implements WidgetEffectBoundsProvider {

    private final Color myUsedColor = JBColor.namedColor("MemoryIndicator.usedBackground", new JBColor(Gray._185, Gray._110));
    private final Color myUnusedColor = JBColor.namedColor("MemoryIndicator.allocatedBackground", new JBColor(Gray._215, Gray._90));

    private long lastCommitedMb = -1;
    private long lastUsedMb = -1;

    private volatile MemoryStats memoryDisplay = new MemoryStats(0, 0, 0);

    MemoryUsagePanelImpl() {
      setFocusable(false);
      setTextAlignment(CENTER_ALIGNMENT);
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent event, int clickCount) {
          if (clickCount == 1) {
            //noinspection CallToSystemGC
            System.gc();
          }
          else if (clickCount == 2) {
            if (JBR.isSystemUtilsSupported()) {
              JBR.getSystemUtils().fullGC();
            }
            else {
              //noinspection CallToSystemGC
              System.gc();
            }
            StorageLockContext.forceDirectMemoryCache();
            DirectByteBufferAllocator.ALLOCATOR.releaseCachedBuffers();
            PlatformMemoryUtil.getInstance().trimLinuxNativeHeap();
          }

          scheduler.request();
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
    public void paintComponent(@NotNull Graphics g) {
      Dimension size = getSize();
      int barWidth = size.width;

      var measured = memoryDisplay;

      long usedMem = measured.usedMem;
      long allocatedMem = measured.allocatedMem;
      long maxMem = measured.maxMem;

      // `maxMem` is 0 until the first async measurement lands; skip the gauge to avoid division by zero.
      int usedBarLength = maxMem > 0 ? (int)(barWidth * usedMem / maxMem) : 0;
      int allocatedBarLength = maxMem > 0 ? (int)(barWidth * allocatedMem / maxMem) : 0;

      boolean isIslandTheme = IslandsState.Companion.isEnabled();
      int arc = isIslandTheme ? JBUI.scale(6) : 0;
      int yOffset = isIslandTheme ? JBUI.scale(3) : 0;
      int hDelta = isIslandTheme ? JBUI.scale(8) : 0;

      GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      g.setColor(UIUtil.getPanelBackground());
      g.fillRoundRect(0, yOffset, barWidth, size.height - hDelta, arc, arc);

      // gauge (allocated)
      g.setColor(myUnusedColor);
      g.fillRoundRect(0, yOffset, allocatedBarLength, size.height - hDelta, arc, arc);

      // gauge (used)
      g.setColor(myUsedColor);
      g.fillRoundRect(0, yOffset, usedBarLength, size.height - hDelta, arc, arc);
      config.restore();

      //text
      super.paintComponent(g);
    }

    @Override
    public @NotNull Rectangle getWidgetEffectBounds() {
      if (IslandsState.Companion.isEnabled()) {
        return new Rectangle(0, JBUI.scale(3), getWidth(), getHeight() - JBUI.scale(8));
      }
      return new Rectangle(0, 0, getWidth(), getHeight());
    }

    @Override
    public @NotNull AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleTextPanel() {
          @Override
          public String getAccessibleName() {
            String text = getText();
            return text != null
                   ? UIBundle.message("memory.usage.panel.accessible.name.with.text", text)
                   : UIBundle.message("memory.usage.panel.accessible.name");
          }
        };
      }
      return accessibleContext;
    }

    @Override
    protected String getTextForPreferredSize() {
      long maxMemoryMb = Registry.is(SHOW_TOTAL_MEMORY_ESTIMATION_REGISTRY_KEY)
                         ? toMb(Runtime.getRuntime().maxMemory()) * 2
                         : toMb(Runtime.getRuntime().maxMemory());
      long sample = maxMemoryMb < 1000 ? 999 :
                    maxMemoryMb < 10_000 ? 9_999 : 99_999;
      //if -Xmx > 100Gb -- well, I'm sorry
      return " " + UIBundle.message("memory.usage.panel.message.text", sample, sample);
    }

    @RequiresEdt
    private void updateState(AppMemoryUsage memoryUsage, MemoryStats runtimeMemory, @Nullable PlatformMemoryUtil.MemoryStats stats) {
      if (!isShowing()) return;

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

      if (stats != null && stats.getRamMinusFileMappings() == 0) {
        stats = null; // In old Windows versions `ramMinusFileMappings` always reports 0
      }
      long fileMappingsRamMb = toMb(stats != null ? stats.getFileMappingsRam() : 0);
      long ramMinusFileMappingsMb = toMb(stats != null ? stats.getRamMinusFileMappings() : 0);
      long ramPlusSwapMinusFileMappings = toMb(stats != null ? stats.getRamPlusSwapMinusFileMappings() : 0);

      var text = Registry.is(SHOW_TOTAL_MEMORY_ESTIMATION_REGISTRY_KEY) ?
                 UIBundle.message("memory.usage.panel.message.text", heapUsedMb, estimatedTotalMemoryUsedMb) :
                 UIBundle.message("memory.usage.panel.message.text", heapUsedMb, heapMaxMb);

      if (heapCommitedMb != lastCommitedMb || heapUsedMb != lastUsedMb || !text.equals(getText())) {
        lastCommitedMb = heapCommitedMb;
        lastUsedMb = heapUsedMb;
        setText(text);

        String i18nBundleKey = stats != null ?
                               "memory.usage.panel.message.tooltip-extended" :
                               "memory.usage.panel.message.tooltip";

        HelpTooltipKt.setToolTipText(
          this,
          HtmlChunk.raw(UIBundle.message(i18nBundleKey,
                                         heapUsedMb, heapCommitedMb, heapMaxMb,
                                         directBuffersFileCacheUsedMb, (directBuffersUsedMb - directBuffersFileCacheUsedMb),
                                         jvmInternalsMb, threadStacksMemoryMb,
                                         estimatedTotalMemoryUsedMb,
                                         memoryMappedFilesMb,
                                         //shown only in .tooltip-extended version:
                                         fileMappingsRamMb, ramMinusFileMappingsMb, ramPlusSwapMinusFileMappings
          ))
        );

        long usedMem;
        long allocatedMem;
        long maxMem;

        if (Registry.is(SHOW_TOTAL_MEMORY_ESTIMATION_REGISTRY_KEY)) {
          // [ heap used | heap commited | total (approx.) commited ]
          maxMem = toMb(memoryUsage.estimatedTotalMemoryUsedBytes());
          allocatedMem = toMb(memoryUsage.heapCommitedBytes);
          usedMem = toMb(memoryUsage.heapUsedBytes);
        }
        else {
          // [ heap used | heap commited | heap max ]
          maxMem = runtimeMemory.maxMem;
          allocatedMem = runtimeMemory.allocatedMem;
          usedMem = runtimeMemory.usedMem;
        }

        this.memoryDisplay = new MemoryStats(usedMem, allocatedMem, maxMem);
      }

      repaint();
    }
  }

  private static final MemoryMXBean MEMORY_MX_BEAN = ManagementFactory.getMemoryMXBean();
  private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();

  static AppMemoryUsage calculateMemoryUsage() {
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

  static final class MemoryStats {
    final long usedMem;
    final long allocatedMem;
    final long maxMem;

    MemoryStats(long usedMem, long allocatedMem, long maxMem) {
      this.usedMem = usedMem;
      this.allocatedMem = allocatedMem;
      this.maxMem = maxMem;
    }
  }

  static final class AppMemoryUsage {
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
