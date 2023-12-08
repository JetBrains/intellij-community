// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.fromNanosecToMillis
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicLong

object WorkspaceFileIndexDataMetrics {
  internal val instancesCounter: AtomicLong = AtomicLong()
  internal val initTimeNanosec: AtomicLong = AtomicLong()
  internal val getFileInfoTimeNanosec: AtomicLong = AtomicLong()
  internal val visitFileSetsTimeNanosec: AtomicLong = AtomicLong()
  internal val processFileSetsTimeNanosec: AtomicLong = AtomicLong()
  internal val markDirtyTimeNanosec: AtomicLong = AtomicLong()
  internal val updateDirtyEntitiesTimeNanosec: AtomicLong = AtomicLong()
  internal val onEntitiesChangedTimeNanosec: AtomicLong = AtomicLong()
  internal val getPackageNameTimeNanosec: AtomicLong = AtomicLong()
  internal val getDirectoriesByPackageNameTimeNanosec: AtomicLong = AtomicLong()

  internal val registerFileSetsTimeNanosec: AtomicLong = AtomicLong()


  private fun setupOpenTelemetryReporting(meter: Meter): Unit {
    val instancesCountCounter = meter.counterBuilder("workspaceModel.workspaceFileIndexData.instances.count").buildObserver()
    val initTimeCounter = meter.counterBuilder("workspaceModel.workspaceFileIndexData.init.ms").buildObserver()
    val getFileInfoTimeCounter = meter.counterBuilder("workspaceModel.workspaceFileIndexData.getFileInfo.ms").buildObserver()
    val visitFileSetsCounter = meter.counterBuilder("workspaceModel.workspaceFileIndexData.visitFileSets.ms").buildObserver()
    val processFileSetsCounter = meter.counterBuilder("workspaceModel.workspaceFileIndexData.processFileSets.ms").buildObserver()
    val markDirtyCounter = meter.counterBuilder("workspaceModel.workspaceFileIndexData.markDirty.ms").buildObserver()
    val updateDirtyEntitiesCounter = meter.counterBuilder("workspaceModel.workspaceFileIndexData.updateDirtyEntities.ms").buildObserver()
    val onEntitiesChangedCounter = meter.counterBuilder("workspaceModel.workspaceFileIndexData.onEntitiesChanged.ms").buildObserver()
    val getPackageNameCounter = meter.counterBuilder("workspaceModel.workspaceFileIndexData.getPackageName.ms").buildObserver()
    val getDirectoriesByPackageNameCounter = meter.counterBuilder("workspaceModel.workspaceFileIndexData.getDirectoriesByPackageName.ms").buildObserver()

    val registerFileSetsMsCounter = meter.counterBuilder("workspaceModel.workspaceFileIndexContributor.registerFileSets.ms").buildObserver()

    meter.batchCallback(
      {
        instancesCountCounter.record(instancesCounter.get())
        initTimeCounter.record(initTimeNanosec.fromNanosecToMillis())
        getFileInfoTimeCounter.record(getFileInfoTimeNanosec.fromNanosecToMillis())
        visitFileSetsCounter.record(visitFileSetsTimeNanosec.fromNanosecToMillis())
        processFileSetsCounter.record(processFileSetsTimeNanosec.fromNanosecToMillis())
        markDirtyCounter.record(markDirtyTimeNanosec.fromNanosecToMillis())
        updateDirtyEntitiesCounter.record(updateDirtyEntitiesTimeNanosec.fromNanosecToMillis())
        onEntitiesChangedCounter.record(onEntitiesChangedTimeNanosec.fromNanosecToMillis())
        getPackageNameCounter.record(getPackageNameTimeNanosec.fromNanosecToMillis())
        getDirectoriesByPackageNameCounter.record(getDirectoriesByPackageNameTimeNanosec.fromNanosecToMillis())

        registerFileSetsMsCounter.record(registerFileSetsTimeNanosec.fromNanosecToMillis())
      },
      instancesCountCounter, initTimeCounter, getFileInfoTimeCounter, visitFileSetsCounter,
      processFileSetsCounter, markDirtyCounter, updateDirtyEntitiesCounter, onEntitiesChangedCounter,
      getPackageNameCounter, getDirectoriesByPackageNameCounter, registerFileSetsMsCounter
    )
  }

  init {
    setupOpenTelemetryReporting(TelemetryManager.getMeter(WorkspaceModel))
  }
}