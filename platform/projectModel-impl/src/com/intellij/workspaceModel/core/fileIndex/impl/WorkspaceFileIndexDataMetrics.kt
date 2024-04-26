// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.NanosecondsMeasurer
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicLong

object WorkspaceFileIndexDataMetrics {
  internal val instancesCounter: AtomicLong = AtomicLong()
  internal val initTimeNanosec = NanosecondsMeasurer()
  internal val getFileInfoTimeNanosec = NanosecondsMeasurer()
  internal val visitFileSetsTimeNanosec = NanosecondsMeasurer()
  internal val processFileSetsTimeNanosec = NanosecondsMeasurer()
  internal val markDirtyTimeNanosec = NanosecondsMeasurer()
  internal val updateDirtyEntitiesTimeNanosec = NanosecondsMeasurer()
  internal val onEntitiesChangedTimeNanosec = NanosecondsMeasurer()
  internal val getPackageNameTimeNanosec = NanosecondsMeasurer()
  internal val getDirectoriesByPackageNameTimeNanosec = NanosecondsMeasurer()

  internal val registerFileSetsTimeNanosec = NanosecondsMeasurer()


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
        initTimeCounter.record(initTimeNanosec.asMilliseconds())
        getFileInfoTimeCounter.record(getFileInfoTimeNanosec.asMilliseconds())
        visitFileSetsCounter.record(visitFileSetsTimeNanosec.asMilliseconds())
        processFileSetsCounter.record(processFileSetsTimeNanosec.asMilliseconds())
        markDirtyCounter.record(markDirtyTimeNanosec.asMilliseconds())
        updateDirtyEntitiesCounter.record(updateDirtyEntitiesTimeNanosec.asMilliseconds())
        onEntitiesChangedCounter.record(onEntitiesChangedTimeNanosec.asMilliseconds())
        getPackageNameCounter.record(getPackageNameTimeNanosec.asMilliseconds())
        getDirectoriesByPackageNameCounter.record(getDirectoriesByPackageNameTimeNanosec.asMilliseconds())

        registerFileSetsMsCounter.record(registerFileSetsTimeNanosec.asMilliseconds())
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