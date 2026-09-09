// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.openapi.application.UI
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@TestApplication
@Timeout(30)
internal class PluginRowReconcilerTest {
  @Test
  fun `revision-only change keeps the existing row`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val fixture = ReconcilerFixture()
    val item = item("plugin.id")

    val first = fixture.reconciler.reconcile(listOf(specification(PluginSectionId.Installed, item, "key"))).single().row
    val secondBinding = fixture.reconciler.reconcile(
      listOf(specification(PluginSectionId.Installed, item.copy(contentRevision = 1), "key"))
    ).single()
    val second = secondBinding.row

    assertThat(second).isSameAs(first)
    assertThat(fixture.createdRows).containsExactly(first)
    assertThat(fixture.createdRenderKeys).containsExactly("key")
    assertThat(secondBinding.item.contentRevision).isEqualTo(1)
    assertThat(fixture.calls).isEmpty()
  }

  @Test
  fun `model or render-key change replaces the row after dependent UI detaches`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val fixture = ReconcilerFixture()
    val item = item("plugin.id")
    val occurrenceId = occurrence(PluginSectionId.Installed, item)
    val first = fixture.reconciler.reconcile(listOf(specification(PluginSectionId.Installed, item, "first"))).single().row

    val second = fixture.reconciler.reconcile(listOf(specification(PluginSectionId.Installed, item, "second"))).single().row
    val thirdItem = item("plugin.id")
    val third = fixture.reconciler.reconcile(listOf(specification(PluginSectionId.Installed, thirdItem, "second"))).single().row

    assertThat(second).isNotSameAs(first)
    assertThat(third).isNotSameAs(second)
    assertThat(fixture.calls).containsExactly(
      "detach:$occurrenceId:$first",
      "release:$first",
      "detach:$occurrenceId:$second",
      "release:$second",
    )
  }

  @Test
  fun `reconciliation follows requested order and releases removed occurrences once`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val fixture = ReconcilerFixture()
    val firstItem = item("first.plugin")
    val secondItem = item("second.plugin")
    val initial = fixture.reconciler.reconcile(
      listOf(
        specification(PluginSectionId.Installed, firstItem, "first"),
        specification(PluginSectionId.Bundled, secondItem, "second"),
      )
    )

    val remaining = fixture.reconciler.reconcile(
      listOf(specification(PluginSectionId.Bundled, secondItem, "second"))
    )
    fixture.reconciler.close()
    fixture.reconciler.close()

    assertThat(remaining.map(PluginRowBinding<String>::occurrenceId))
      .containsExactly(occurrence(PluginSectionId.Bundled, secondItem))
    assertThat(remaining.single().row).isSameAs(initial[1].row)
    assertThat(fixture.calls).containsExactly(
      "detach:${initial[0].occurrenceId}:${initial[0].row}",
      "release:${initial[0].row}",
      "detach:${initial[1].occurrenceId}:${initial[1].row}",
      "release:${initial[1].row}",
    )
  }

  @Test
  fun `failed replacement keeps current rows and releases provisional rows`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val fixture = ReconcilerFixture(failPluginId = "failed.plugin")
    val retainedItem = item("retained.plugin")
    val retainedSpecification = specification(PluginSectionId.Installed, retainedItem, "first")
    val retainedRow = fixture.reconciler.reconcile(listOf(retainedSpecification)).single().row
    val provisionalItem = item("provisional.plugin")
    val failedItem = item("failed.plugin")

    assertThatThrownBy {
      fixture.reconciler.reconcile(
        listOf(
          specification(PluginSectionId.Installed, retainedItem, "second"),
          specification(PluginSectionId.Bundled, provisionalItem, "provisional"),
          specification(PluginSectionId.Suggested, failedItem, "failed"),
        )
      )
    }.isInstanceOf(IllegalStateException::class.java)

    assertThat(fixture.reconciler.row(retainedSpecification.occurrenceId)).isSameAs(retainedRow)
    assertThat(fixture.calls).containsExactly("release:row-2", "release:row-3")
  }

  @Test
  fun `invalid occurrence input is rejected before rows change`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val fixture = ReconcilerFixture()
    val item = item("plugin.id")
    val specification = specification(PluginSectionId.Installed, item, "key")
    val row = fixture.reconciler.reconcile(listOf(specification)).single().row

    assertThatThrownBy {
      fixture.reconciler.reconcile(listOf(specification, specification))
    }.isInstanceOf(IllegalArgumentException::class.java)
    assertThatThrownBy {
      fixture.reconciler.reconcile(
        listOf(
          PluginRowSpecification(
            PluginOccurrenceId(PluginSectionId.Installed, PluginId.getId("other.plugin")),
            item,
            "key",
          )
        )
      )
    }.isInstanceOf(IllegalArgumentException::class.java)

    assertThat(fixture.reconciler.row(specification.occurrenceId)).isSameAs(row)
    assertThat(fixture.calls).isEmpty()
  }

  private class ReconcilerFixture(failPluginId: String? = null) {
    val createdRows = ArrayList<String>()
    val createdRenderKeys = ArrayList<String>()
    val calls = ArrayList<String>()
    private var nextRowId = 1
    val reconciler = PluginRowReconciler<String, String>(
      createRow = { occurrenceId, _, renderKey ->
        check(occurrenceId.pluginId.idString != failPluginId) { "Row creation failed" }
        val row = "row-${nextRowId++}"
        createdRows.add(row)
        createdRenderKeys.add(renderKey)
        row
      },
      beforeRelease = { occurrenceId, row -> calls.add("detach:$occurrenceId:$row") },
      releaseRow = { row -> calls.add("release:$row") },
    )
  }

  private fun item(pluginId: String): PluginItemState {
    val id = PluginId.getId(pluginId)
    val model = PluginNodeModelBuilderFactory.createBuilder(id).setName(pluginId).build()
    return PluginItemState(id, pluginId, modelHandle = PluginItemModelHandle(model))
  }

  private fun specification(
    sectionId: PluginSectionId,
    item: PluginItemState,
    renderKey: String,
  ): PluginRowSpecification<String> {
    return PluginRowSpecification(occurrence(sectionId, item), item, renderKey)
  }

  private fun occurrence(sectionId: PluginSectionId, item: PluginItemState): PluginOccurrenceId {
    return PluginOccurrenceId(sectionId, item.pluginId)
  }
}
