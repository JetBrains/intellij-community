// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class PluginUpdateProgressTest {
  @Test
  fun `progress indicator reports the plugin and exact fraction`() {
    val pluginId = PluginId.getId("first")
    val events = mutableListOf<ProgressEvent>()
    val indicator = TextRecordingProgressIndicator().withPluginUpdateProgress(pluginId, "First") { id, fraction ->
      events += ProgressEvent(id, fraction)
    }

    indicator.isIndeterminate = true
    indicator.fraction = 0.123

    assertThat(events).containsExactly(
      ProgressEvent(pluginId, null),
      ProgressEvent(pluginId, 0.123),
    )
    assertThat(indicator.text).isEqualTo(IdeBundle.message("progress.downloading.plugin", "First"))
  }

  @Test
  fun `legacy progress keeps the general download text`() {
    val indicator = TextRecordingProgressIndicator()
    indicator.text = IdeBundle.message("update.downloading.plugins.progress")

    indicator.withPluginUpdateProgress(PluginId.getId("first"), "First", PluginUpdateProgressSink.NONE)

    assertThat(indicator.text).isEqualTo(IdeBundle.message("update.downloading.plugins.progress"))
  }

  @Test
  fun `whole percent limiting is independent per plugin and keeps the exact fraction`() {
    val first = PluginId.getId("first")
    val second = PluginId.getId("second")
    val events = mutableListOf<ProgressEvent>()
    val sink = PluginUpdateProgressSink { id, fraction -> events += ProgressEvent(id, fraction) }
      .withWholePercentDownloadProgress()

    sink.downloadProgressChanged(first, 0.101)
    sink.downloadProgressChanged(first, 0.109)
    sink.downloadProgressChanged(second, 0.109)
    sink.downloadProgressChanged(first, 0.111)

    assertThat(events).containsExactly(
      ProgressEvent(first, 0.101),
      ProgressEvent(second, 0.109),
      ProgressEvent(first, 0.111),
    )
  }

  @Test
  fun `combined progress uses one side directly and averages two known sides`() {
    val localPlugin = PluginId.getId("local")
    val splitPlugin = PluginId.getId("split")
    val events = mutableListOf<ProgressEvent>()
    val combiner = PluginUpdateProgressCombiner(
      expectedSides = mapOf(
        localPlugin to setOf(PluginUpdateProgressSide.LOCAL),
        splitPlugin to PluginUpdateProgressSide.entries.toSet(),
      ),
      delegate = PluginUpdateProgressSink { id, fraction -> events += ProgressEvent(id, fraction) },
    )
    val local = combiner.sink(PluginUpdateProgressSide.LOCAL)
    val remote = combiner.sink(PluginUpdateProgressSide.REMOTE)

    local.downloadProgressChanged(localPlugin, 0.25)
    local.downloadProgressChanged(splitPlugin, 0.25)
    remote.downloadProgressChanged(splitPlugin, 0.75)
    remote.downloadProgressChanged(splitPlugin, null)

    assertThat(events).containsExactly(
      ProgressEvent(localPlugin, 0.25),
      ProgressEvent(splitPlugin, null),
      ProgressEvent(splitPlugin, 0.5),
      ProgressEvent(splitPlugin, null),
    )
  }

  private class TextRecordingProgressIndicator : EmptyProgressIndicator() {
    private var progressText = ""

    override fun setText(text: String?) {
      progressText = text.orEmpty()
    }

    override fun getText(): String = progressText
  }

  private data class ProgressEvent(val pluginId: PluginId, val fraction: Double?)
}
