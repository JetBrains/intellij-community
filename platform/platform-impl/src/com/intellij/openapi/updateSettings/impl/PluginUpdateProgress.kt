// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.EnumMap

internal fun ProgressIndicator.withPluginUpdateProgress(
  pluginId: PluginId,
  pluginName: @Nls String,
  progressSink: PluginUpdateProgressSink,
): ProgressIndicator {
  val indicator = this
  if (progressSink !== PluginUpdateProgressSink.NONE) {
    indicator.text = IdeBundle.message("progress.downloading.plugin", pluginName)
  }
  return object : ProgressIndicator by indicator {
    override fun setFraction(fraction: Double) {
      indicator.fraction = fraction
      progressSink.downloadProgressChanged(pluginId, fraction)
    }

    override fun setIndeterminate(indeterminate: Boolean) {
      indicator.isIndeterminate = indeterminate
      progressSink.downloadProgressChanged(pluginId, if (indeterminate) null else indicator.fraction)
    }

    override fun popState() {
      indicator.popState()
      progressSink.downloadProgressChanged(pluginId, if (indicator.isIndeterminate) null else indicator.fraction)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun startNonCancelableSection() {
      indicator.startNonCancelableSection()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun finishNonCancelableSection() {
      indicator.finishNonCancelableSection()
    }
  }
}

@ApiStatus.Internal
fun PluginUpdateProgressSink.withWholePercentDownloadProgress(): PluginUpdateProgressSink {
  val delegate = this
  val lock = Any()
  val lastReportedPercent = HashMap<PluginId, Int?>()
  return PluginUpdateProgressSink { pluginId, fraction ->
    val percent = fraction?.let { (it * 100).toInt() }
    synchronized(lock) {
      if (lastReportedPercent.containsKey(pluginId) && lastReportedPercent[pluginId] == percent) return@synchronized

      lastReportedPercent[pluginId] = percent
      delegate.downloadProgressChanged(pluginId, fraction)
    }
  }
}

@ApiStatus.Internal
enum class PluginUpdateProgressSide {
  LOCAL,
  REMOTE,
}

@ApiStatus.Internal
class PluginUpdateProgressCombiner(
  private val expectedSides: Map<PluginId, Set<PluginUpdateProgressSide>>,
  private val delegate: PluginUpdateProgressSink,
) {
  private val lock = Any()
  private val progress = HashMap<PluginId, MutableMap<PluginUpdateProgressSide, Double?>>()

  fun sink(side: PluginUpdateProgressSide): PluginUpdateProgressSink {
    return PluginUpdateProgressSink { pluginId, fraction ->
      val combined = synchronized(lock) {
        val expected = expectedSides[pluginId]
        if (expected == null || side !in expected) return@PluginUpdateProgressSink

        val progressBySide = progress.getOrPut(pluginId) { EnumMap(PluginUpdateProgressSide::class.java) }
        progressBySide[side] = fraction
        if (expected.any { it !in progressBySide || progressBySide[it] == null }) {
          null
        }
        else {
          expected.map { progressBySide.getValue(it)!! }.average()
        }
      }
      delegate.downloadProgressChanged(pluginId, combined)
    }
  }
}
