// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.cachedValueProfiler;

import com.google.gson.stream.JsonWriter;
import com.intellij.psi.util.CachedValueProfiler;
import com.intellij.psi.util.ProfilingInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static java.time.temporal.ChronoField.*;

public class CachedValueProfilerDumper {
  private final MultiMap<StackTraceElement, ProfilingInfo> myStorage;

  private CachedValueProfilerDumper(MultiMap<StackTraceElement, ProfilingInfo> storage) {
    myStorage = storage;
  }

  @NotNull
  public static File dumpResults(@Nullable File dir) throws IOException {
    CachedValueProfiler profiler = CachedValueProfiler.getInstance();
    CachedValueProfilerDumper dumper = new CachedValueProfilerDumper(profiler.getStorageSnapshot());
    return dumper.dump(dir);
  }

  @NotNull
  private File dump(@Nullable File dir) throws IOException {
    List<TotalInfo> infos = prepareInfo();

    String fileName = String.format("dump-%s.cvp", time());
    File file = new File(dir, fileName);
    try (JsonWriter writer = new JsonWriter(new BufferedWriter(new FileWriter(file)))) {
      new MyWriter(writer).write(infos);
    }
    return file;
  }

  @NotNull
  private List<TotalInfo> prepareInfo() {
    List<TotalInfo> list = ContainerUtil.newArrayList();
    myStorage.entrySet().forEach((entry) -> list.add(new TotalInfo(entry.getKey(), entry.getValue())));

    Collections.sort(list, Comparator.comparing(info -> ((double)info.getTotalUseCount()) / info.getInfos().size()));
    return list;
  }

  private static class TotalInfo {
    private final StackTraceElement myOrigin;
    private final long myTotalLifeTime;
    private final long myTotalUseCount;

    private final List<ProfilingInfo> myInfos;

    private TotalInfo(@NotNull StackTraceElement origin, @NotNull Collection<ProfilingInfo> infos) {
      myOrigin = origin;
      myInfos = Collections.unmodifiableList(ContainerUtil.newArrayList(infos));

      myTotalLifeTime = myInfos.stream().mapToLong(value -> value.getLifetime()).sum();
      myTotalUseCount = myInfos.stream().mapToLong(value -> value.getUseCount()).sum();
    }

    @NotNull
    private StackTraceElement getOrigin() {
      return myOrigin;
    }

    private long getTotalLifeTime() {
      return myTotalLifeTime;
    }

    private long getTotalUseCount() {
      return myTotalUseCount;
    }

    @NotNull
    private List<ProfilingInfo> getInfos() {
      return myInfos;
    }
  }

  @NotNull
  private static String time() {
    DateTimeFormatter formatter = new DateTimeFormatterBuilder()
      .appendValue(HOUR_OF_DAY, 2)
      .appendLiteral(':')
      .appendValue(MINUTE_OF_HOUR, 2)
      .optionalStart().appendLiteral(':').appendValue(SECOND_OF_MINUTE, 2)
      .appendLiteral('|')
      .appendValue(YEAR)
      .appendLiteral('-')
      .appendValue(MONTH_OF_YEAR, 2)
      .appendLiteral('-')
      .appendValue(DAY_OF_MONTH, 2)
      .toFormatter();
    return LocalDateTime.now().format(formatter);
  }

  private static class MyWriter {
    private final JsonWriter myWriter;

    private MyWriter(@NotNull JsonWriter writer) {
      myWriter = writer;
    }

    private void write(@NotNull List<TotalInfo> infos) throws IOException {
      myWriter.setIndent("  ");
      myWriter.beginArray();
      for (TotalInfo info : infos) {
        writeInfo(info);
      }
      myWriter.endArray();
    }

    private void writeInfo(@NotNull TotalInfo info) throws IOException {
      myWriter.beginObject();

      String origin = info.getOrigin().toString();
      long totalLifeTime = info.getTotalLifeTime();
      long totalUseCount = info.getTotalUseCount();
      int createdCount = info.getInfos().size();

      myWriter.name("origin").value(origin);
      myWriter.name("total lifetime").value(totalLifeTime);
      myWriter.name("total use count").value(totalUseCount);
      myWriter.name("created").value(createdCount);
      myWriter.endObject();
    }
  }
}
