// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.mapped;

import com.intellij.util.io.dev.mmapped.MMappedFileStorage;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import org.jetbrains.annotations.NotNull;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

/**
 * Reports to OTel.Metrics keys numbers from {@link MMappedFileStorage} -- total storages in use,
 * total bytes mapped, etc (see ctor for all metrics)
 */
public class MappedStorageOTelMonitor implements AutoCloseable {

  private final BatchCallback otelCallback;

  public MappedStorageOTelMonitor(@NotNull Meter meter) {
    ObservableLongMeasurement storagesCounter = meter.gaugeBuilder("MappedFileStorage.storages")
      .setDescription("MappedFileStorage instances in operation at the moment")
      .ofLongs()
      .buildObserver();
    ObservableLongMeasurement pagesCounter = meter.gaugeBuilder("MappedFileStorage.totalPagesMapped")
      .setDescription("MappedFileStorage.Page instances in operation at the moment")
      .ofLongs()
      .buildObserver();
    ObservableLongMeasurement pagesBytesCounter = meter.gaugeBuilder("MappedFileStorage.totalBytesMapped")
      .setDescription("Total size of MappedByteBuffers in use by MappedFileStorage at the moment")
      .setUnit("bytes")
      .ofLongs()
      .buildObserver();
    ObservableLongMeasurement mappingTimeCounter = meter.gaugeBuilder("MappedFileStorage.totalTimeSpentOnMappingUs")
      .setDescription("Total time (us) spent inside Page.map() method (file expansion/zeroing, + mmap)")
      .setUnit("us")
      .ofLongs()
      .buildObserver();

    otelCallback = meter.batchCallback(
      () -> {
        storagesCounter.record(MMappedFileStorage.openedStoragesCount());
        pagesCounter.record(MMappedFileStorage.totalPagesMapped());
        pagesBytesCounter.record(MMappedFileStorage.totalBytesMapped());
        mappingTimeCounter.record(MMappedFileStorage.totalTimeForPageMap(MICROSECONDS));
      },
      storagesCounter, pagesCounter, pagesBytesCounter, mappingTimeCounter
    );
  }

  @Override
  public void close() throws Exception {
    otelCallback.close();
  }
}
