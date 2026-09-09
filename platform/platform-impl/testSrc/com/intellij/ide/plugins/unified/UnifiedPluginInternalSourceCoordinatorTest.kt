// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class UnifiedPluginInternalSourceCoordinatorTest {
  @Test
  fun `internal group is enriched and published with its custom title`() = runTest {
    val enricher = RecordingEnricher()
    val coordinator = UnifiedPluginInternalSourceCoordinator(
      scope = backgroundScope,
      loadGroup = { UnifiedPluginInternalGroup("Managed plugins", listOf(plugin("managed.plugin"))) },
      dataEnricher = enricher,
      updates = emptyFlow(),
      loadingTitle = "Internal plugins",
      loadErrorMessage = "Unable to load internal plugins",
    )

    coordinator.start()
    runCurrent()

    val section = coordinator.state.value.section
    assertThat(section?.id).isEqualTo(PluginSectionId.Internal)
    assertThat(section?.title).isEqualTo("Managed plugins")
    assertThat(section?.items?.map { it.pluginId.idString }).containsExactly("managed.plugin")
    assertThat(section?.status).isEqualTo(PluginSectionStatus.Ready)
    assertThat(enricher.enrichCount).isEqualTo(1)
    assertThat(coordinator.state.value.descriptorRequestSettled).isTrue()
    coordinator.close()
  }

  @Test
  fun `missing internal group does not publish a section or enrich data`() = runTest {
    val enricher = RecordingEnricher()
    val coordinator = UnifiedPluginInternalSourceCoordinator(
      scope = backgroundScope,
      loadGroup = { null },
      dataEnricher = enricher,
      updates = emptyFlow(),
      loadingTitle = "Internal plugins",
      loadErrorMessage = "Unable to load internal plugins",
    )

    coordinator.start()
    runCurrent()
    advanceTimeBy(100.milliseconds)
    runCurrent()

    assertThat(coordinator.state.value.section).isNull()
    assertThat(coordinator.state.value.facetsLoading).isFalse()
    assertThat(coordinator.state.value.descriptorRequestSettled).isTrue()
    assertThat(enricher.enrichCount).isZero()
    coordinator.close()
  }

  @Test
  fun `slow internal group publishes a delayed loading section`() = runTest {
    val group = CompletableDeferred<UnifiedPluginInternalGroup?>()
    val coordinator = UnifiedPluginInternalSourceCoordinator(
      scope = backgroundScope,
      loadGroup = { group.await() },
      dataEnricher = RecordingEnricher(),
      updates = emptyFlow(),
      loadingTitle = "Internal plugins",
      loadErrorMessage = "Unable to load internal plugins",
    )

    coordinator.start()
    runCurrent()
    advanceTimeBy(99.milliseconds)
    runCurrent()

    assertThat(coordinator.state.value.section).isNull()
    assertThat(coordinator.state.value.descriptorRequestSettled).isFalse()

    advanceTimeBy(1.milliseconds)
    runCurrent()

    val loadingSection = coordinator.state.value.section
    assertThat(loadingSection?.title).isEqualTo("Internal plugins")
    assertThat(loadingSection?.status).isEqualTo(PluginSectionStatus.Loading(showingStaleContent = false))
    assertThat(coordinator.state.value.facetsLoading).isTrue()
    assertThat(coordinator.state.value.descriptorRequestSettled).isFalse()

    group.complete(UnifiedPluginInternalGroup("Managed plugins", listOf(plugin("managed.plugin"))))
    runCurrent()

    assertThat(coordinator.state.value.section?.title).isEqualTo("Managed plugins")
    assertThat(coordinator.state.value.section?.status).isEqualTo(PluginSectionStatus.Ready)
    assertThat(coordinator.state.value.descriptorRequestSettled).isTrue()
    coordinator.close()
  }

  @Test
  fun `slow missing internal group removes the delayed loading section`() = runTest {
    val group = CompletableDeferred<UnifiedPluginInternalGroup?>()
    val coordinator = UnifiedPluginInternalSourceCoordinator(
      scope = backgroundScope,
      loadGroup = { group.await() },
      dataEnricher = RecordingEnricher(),
      updates = emptyFlow(),
      loadingTitle = "Internal plugins",
      loadErrorMessage = "Unable to load internal plugins",
    )

    coordinator.start()
    runCurrent()
    advanceTimeBy(100.milliseconds)
    runCurrent()

    assertThat(coordinator.state.value.section?.title).isEqualTo("Internal plugins")

    group.complete(null)
    runCurrent()

    assertThat(coordinator.state.value.section).isNull()
    assertThat(coordinator.state.value.facetsLoading).isFalse()
    coordinator.close()
  }

  @Test
  fun `initial descriptor failure publishes a retryable section`() = runTest {
    var loadCount = 0
    val coordinator = UnifiedPluginInternalSourceCoordinator(
      scope = backgroundScope,
      loadGroup = {
        loadCount++
        if (loadCount == 1) error("descriptor failed")
        UnifiedPluginInternalGroup("Managed plugins", listOf(plugin("managed.plugin")))
      },
      dataEnricher = RecordingEnricher(),
      updates = emptyFlow(),
      loadingTitle = "Internal plugins",
      loadErrorMessage = "Unable to load internal plugins",
    )

    coordinator.start()
    runCurrent()

    val failedSection = coordinator.state.value.section
    assertThat(failedSection?.title).isEqualTo("Internal plugins")
    assertThat(failedSection?.items).isEmpty()
    assertThat(failedSection?.status).isEqualTo(
      PluginSectionStatus.Failed(PluginSectionError("Unable to load internal plugins", retryable = true))
    )
    assertThat(coordinator.state.value.descriptorRequestSettled).isTrue()

    coordinator.retry()
    runCurrent()

    assertThat(coordinator.state.value.section?.title).isEqualTo("Managed plugins")
    assertThat(coordinator.state.value.section?.status).isEqualTo(PluginSectionStatus.Ready)
    coordinator.close()
  }

  @Test
  fun `plugin updates refresh internal row data`() = runTest {
    val updates = MutableSharedFlow<PluginUpdatesEvent>(extraBufferCapacity = 1)
    val enricher = RecordingEnricher()
    val coordinator = UnifiedPluginInternalSourceCoordinator(
      scope = backgroundScope,
      loadGroup = { UnifiedPluginInternalGroup("Managed plugins", listOf(plugin("managed.plugin"))) },
      dataEnricher = enricher,
      updates = updates,
      loadingTitle = "Internal plugins",
      loadErrorMessage = "Unable to load internal plugins",
    )
    coordinator.start()
    runCurrent()

    assertThat(updates.tryEmit(PluginUpdatesEvent(emptyList(), emptyList(), emptyList()))).isTrue()
    runCurrent()

    assertThat(enricher.enrichCount).isEqualTo(2)
    assertThat(coordinator.state.value.section?.status).isEqualTo(PluginSectionStatus.Ready)
    coordinator.close()
  }

  private class RecordingEnricher : UnifiedPluginRemoteDataEnricher {
    var enrichCount = 0

    override suspend fun enrich(
      models: List<PluginUiModel>,
      updates: PluginUpdatesEvent?,
      contentRevision: Long,
    ): UnifiedPluginMarketplaceSnapshot {
      enrichCount++
      return UnifiedPluginMarketplaceSnapshot(
        items = models.map { model ->
          PluginItemState(
            pluginId = model.pluginId,
            name = model.name,
            contentRevision = contentRevision,
            modelHandle = PluginItemModelHandle(model),
          )
        },
        listModelData = PluginListModelData.EMPTY,
      )
    }
  }

  private companion object {
    fun plugin(id: String): PluginDto = PluginDto(id, PluginId.getId(id))
  }
}
