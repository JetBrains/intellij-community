// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.registry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.EditorPainter
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class EditorRegistryTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private lateinit var registryService: EditorRegistryService

  @BeforeEach
  fun setUp() {
    registryService = ApplicationManager.getApplication().service<EditorRegistryService>()
  }

  @Test
  @RegistryKey(key = "editor.drag.sensitivity", value = "7")
  fun testRegistryChangePublishesSnapshot() {
    assertEquals(7, registryService.snapshot().dragSensitivity())
    assertEquals(7, EditorImpl.dragSensitivity())
  }

  @Test
  @RegistryKey(key = "editor.drag.sensitivity", value = "30")
  fun testDragSensitivityStaysInRange() {
    assertEquals(25, registryService.snapshot().dragSensitivity())
  }

  @Test
  fun testListenerReceivesChangeOfEditorKey() {
    val changes = subscribe()

    Registry.get("editor.show.right.margin.in.read.only.files").setValue(false, disposable)

    assertEquals(1, changes.size)
    val (oldSnapshot, newSnapshot) = changes.single()
    assertTrue(oldSnapshot.isRightMarginShownInReadOnlyFiles())
    assertFalse(newSnapshot.isRightMarginShownInReadOnlyFiles())
    assertSame(registryService.snapshot(), newSnapshot)
  }

  @Test
  fun testChangeOfOtherKeyDoesNotPublish() {
    val snapshot = registryService.snapshot()
    val changes = subscribe()

    Registry.get("ide.tooltip.dismissDelay").setValue(5000, disposable)

    assertTrue(changes.isEmpty())
    assertSame(snapshot, registryService.snapshot())
  }

  @Test
  fun testChangeOfPaintedKeyNeedsRepaint() {
    val changes = subscribe()

    Registry.get("editor.highlight.foldings").setValue(false, disposable)

    val (oldSnapshot, newSnapshot) = changes.single()
    assertTrue(EditorPainter.needsRepaint(oldSnapshot, newSnapshot))
  }

  @Test
  fun testChangeOfDragSensitivityNeedsNoRepaint() {
    val changes = subscribe()

    Registry.get("editor.drag.sensitivity").setValue(7, disposable)

    val (oldSnapshot, newSnapshot) = changes.single()
    assertFalse(EditorPainter.needsRepaint(oldSnapshot, newSnapshot))
  }

  private fun subscribe(): List<Pair<EditorRegistry, EditorRegistry>> {
    val changes = ArrayList<Pair<EditorRegistry, EditorRegistry>>()
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(
      EditorRegistryListener.TOPIC,
      EditorRegistryListener { oldSnapshot, newSnapshot -> changes.add(oldSnapshot to newSnapshot) },
    )
    return changes
  }
}
