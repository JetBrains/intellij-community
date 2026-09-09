// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@TestApplication
internal class PluginInstallationProgressTest {
  @Test
  fun `download indicator forwards progress and cancellation`() {
    val delegate = ProgressIndicatorBase().apply { isIndeterminate = false }
    val progressSink = RecordingProgressSink()
    val indicator = delegate.withDownloadProgressSink(progressSink)

    indicator.fraction = 0.42
    assertThat(delegate.fraction).isEqualTo(0.42)

    indicator.isIndeterminate = true
    indicator.cancel()

    assertThat(delegate.isIndeterminate).isTrue()
    assertThat(delegate.isCanceled).isTrue()
    assertThat(progressSink.fractions).containsExactly(0.42, null)
    assertThatThrownBy { indicator.checkCanceled() }.isInstanceOf(ProcessCanceledException::class.java)
  }

  @Test
  fun `visible indicator follows download progress and forwards every event`() {
    val indicator = PluginDownloadBgProgressIndicator()
    val progressSink = RecordingProgressSink()
    val sink = progressSink.withDownloadProgressIndicator(indicator)

    sink.downloadProgressChanged(null)
    assertThat(indicator.isIndeterminate).isTrue()

    sink.downloadProgressChanged(0.37)
    assertThat(indicator.isIndeterminate).isFalse()
    assertThat(indicator.fraction).isEqualTo(0.37)
    assertThat(progressSink.fractions).containsExactly(null, 0.37)
  }

  @Test
  fun `whole percent sink limits only download progress`() {
    val progressSink = RecordingProgressSink()
    val sink = progressSink.withWholePercentDownloadProgress()

    sink.downloadProgressChanged(0.371)
    sink.downloadProgressChanged(0.379)
    sink.dependenciesScheduled(emptyList())
    sink.downloadProgressChanged(0.381)
    sink.downloadProgressChanged(0.041)
    sink.downloadProgressChanged(null)
    sink.downloadProgressChanged(null)

    assertThat(progressSink.fractions).containsExactly(0.371, 0.381, 0.041, null)
    assertThat(progressSink.dependenciesEvents).isEqualTo(1)
  }

  private class RecordingProgressSink : PluginInstallationProgressSink {
    val fractions = mutableListOf<Double?>()
    var dependenciesEvents = 0

    override fun dependenciesScheduled(dependencies: List<PluginUiModel>) {
      dependenciesEvents++
    }

    override fun downloadProgressChanged(fraction: Double?) {
      fractions.add(fraction)
    }
  }
}
