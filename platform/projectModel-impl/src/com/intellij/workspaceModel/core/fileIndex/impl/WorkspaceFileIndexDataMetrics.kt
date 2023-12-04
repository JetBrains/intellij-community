// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.WorkspaceModel
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicLong

object WorkspaceFileIndexDataMetrics {
  internal val instancesCounter: AtomicLong = AtomicLong()
  internal val initTimeMs: AtomicLong = AtomicLong()
  internal val getFileInfoTimeMs: AtomicLong = AtomicLong()
  internal val visitFileSetsTimeMs: AtomicLong = AtomicLong()
  internal val processFileSetsTimeMs: AtomicLong = AtomicLong()
  internal val markDirtyTimeMs: AtomicLong = AtomicLong()
  internal val updateDirtyEntitiesTimeMs: AtomicLong = AtomicLong()
  internal val onEntitiesChangedTimeMs: AtomicLong = AtomicLong()
  internal val getPackageNameTimeNanosec: AtomicLong = AtomicLong()
  internal val getDirectoriesByPackageNameTimeMs: AtomicLong = AtomicLong()

  internal val registerFileSetsTimeNanosec: AtomicLong = AtomicLong()


  private fun setupOpenTelemetryReporting(meter: Meter): Unit {
    val instancesCountGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.instances.count")
      .ofLongs().buildObserver()
    val initTimeGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.init.ms")
      .ofLongs().buildObserver()
    val getFileInfoTimeGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.getFileInfo.ms")
      .ofLongs().setDescription("Total time spent in method").buildObserver()
    val visitFileSetsGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.visitFileSets.ms")
      .ofLongs().setDescription("Total time spent in method").buildObserver()
    val processFileSetsGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.processFileSets.ms")
      .ofLongs().setDescription("Total time spent in method").buildObserver()
    val markDirtyGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.markDirty.ms")
      .ofLongs().setDescription("Total time spent in method").buildObserver()
    val updateDirtyEntitiesGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.updateDirtyEntities.ms")
      .ofLongs().setDescription("Total time spent in method").buildObserver()
    val onEntitiesChangedGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.onEntitiesChanged.ms")
      .ofLongs().setDescription("Total time spent in method").buildObserver()
    val getPackageNameGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.getPackageName.ms")
      .ofLongs().setDescription("Total time spent in method").buildObserver()
    val getDirectoriesByPackageNameGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.getDirectoriesByPackageName.ms")
      .ofLongs().setDescription("Total time spent in method").buildObserver()

    val registerFileSetsMsGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexContributor.registerFileSets.ms")
      .ofLongs().buildObserver()

    meter.batchCallback(
      {
        instancesCountGauge.record(instancesCounter.get())
        initTimeGauge.record(initTimeMs.get())
        getFileInfoTimeGauge.record(getFileInfoTimeMs.get())
        visitFileSetsGauge.record(visitFileSetsTimeMs.get())
        processFileSetsGauge.record(processFileSetsTimeMs.get())
        markDirtyGauge.record(markDirtyTimeMs.get())
        updateDirtyEntitiesGauge.record(updateDirtyEntitiesTimeMs.get())
        onEntitiesChangedGauge.record(onEntitiesChangedTimeMs.get())
        getPackageNameGauge.record(getPackageNameTimeNanosec.get() / 1000)
        getDirectoriesByPackageNameGauge.record(getDirectoriesByPackageNameTimeMs.get())

        registerFileSetsMsGauge.record(registerFileSetsTimeNanosec.get() / 1000)
      },
      instancesCountGauge, initTimeGauge, getFileInfoTimeGauge, visitFileSetsGauge,
      processFileSetsGauge, markDirtyGauge, updateDirtyEntitiesGauge, onEntitiesChangedGauge,
      getPackageNameGauge, getDirectoriesByPackageNameGauge, registerFileSetsMsGauge
    )
  }

  init {
    setupOpenTelemetryReporting(TelemetryManager.getMeter(WorkspaceModel))
  }
}