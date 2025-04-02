// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.jps;

import com.google.gson.Gson;
import com.intellij.util.containers.ContainerUtil;
import com.sun.management.OperatingSystemMXBean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage;

import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.intellij.compilation.charts.jps.ChartsBuilderService.COMPILATION_STATISTIC_BUILDER_ID;

public final class CompileStatisticBuilderMessage extends CustomBuilderMessage {
  private static final Gson JSON = new Gson();

  private CompileStatisticBuilderMessage(@NotNull String messageType, @NotNull String data) {
    super(COMPILATION_STATISTIC_BUILDER_ID, messageType, data);
  }

  public static @NotNull CompileStatisticBuilderMessage create(@NotNull Set<? extends BuildTarget<?>> targets,
                                                               @NotNull String event) {
    List<TargetEvent>
      events = ContainerUtil.map(targets, target -> map(target, event.equals("STARTED")
                                                                ? StartTarget::new
                                                                : FinishTarget::new));
    return new CompileStatisticBuilderMessage(event, JSON.toJson(events));
  }

  private static @NotNull <T extends TargetEvent> T map(@NotNull BuildTarget<?> target,
                                                        @NotNull Supplier<T> event) {
    T data = event.get();
    data.name = target instanceof ModuleBasedTarget
                ? ((ModuleBasedTarget<?>)target).getModule().getName() :
                target.getId();
    data.type = target.getTargetType().getTypeId();
    data.isFileBased = target.getTargetType().isFileBased();
    data.isTest = target.getTargetType() instanceof JavaModuleBuildTargetType &&
                  ((JavaModuleBuildTargetType)target.getTargetType()).isTests();
    return data;
  }

  public static @Nullable BuildMessage create(@NotNull MemoryMXBean memory, @NotNull OperatingSystemMXBean os) {
    CpuMemoryStatistics statistics = new CpuMemoryStatistics();
    statistics.heapUsed = memory.getHeapMemoryUsage().getUsed();
    statistics.heapMax = memory.getHeapMemoryUsage().getMax();
    statistics.nonHeapUsed = memory.getNonHeapMemoryUsage().getUsed();
    statistics.nonHeapMax = memory.getNonHeapMemoryUsage().getMax();

    double cpuLoad = 0;
    int maxRetries = 5;
    while (maxRetries --> 0 && cpuLoad <= 0) {
      cpuLoad = os.getSystemCpuLoad();
    }
    if (cpuLoad < 0) return null;
    statistics.cpu = (long)(cpuLoad * 100);

    return new CompileStatisticBuilderMessage("STATISTIC", JSON.toJson(statistics));
  }

  public abstract static class TargetEvent {
    public String name;
    public String type;
    public boolean isTest;
    public boolean isFileBased;
    public long time = System.nanoTime();
    public long thread = Thread.currentThread().getId();
  }

  public static class StartTarget extends TargetEvent {
  }

  public static class FinishTarget extends TargetEvent {
  }

  public static class CpuMemoryStatistics {
    public long heapUsed;
    public long heapMax;
    public long nonHeapUsed;
    public long nonHeapMax;
    public long cpu;
    public long time = System.nanoTime();
  }
}
