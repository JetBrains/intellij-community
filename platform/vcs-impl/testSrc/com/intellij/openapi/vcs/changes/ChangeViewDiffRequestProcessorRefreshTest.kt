// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.ChangeWrapper
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.function.BooleanSupplier

class ChangeViewDiffRequestProcessorRefreshTest {
  @Test
  fun `fake before or after revision is a placeholder`() {
    val file = file()
    assertTrue(ChangeViewDiffRequestProcessor.isPlaceholder(Change(fake(file), real(file))))
    assertTrue(ChangeViewDiffRequestProcessor.isPlaceholder(Change(real(file), fake(file))))
    assertTrue(ChangeViewDiffRequestProcessor.isPlaceholder(Change(null, fake(file))))
  }

  @Test
  fun `real change and real changelist change are not placeholders`() {
    val file = file()
    val change = Change(real(file), real(file))
    assertFalse(ChangeViewDiffRequestProcessor.isPlaceholder(change))
    assertFalse(ChangeViewDiffRequestProcessor.isPlaceholder(ChangeListChange(change, "Changes", "id")))
  }

  @Test
  fun `focused model refresh keeps real current change without match`() {
    val current = ChangeWrapper(Change(real(file()), real(file())))
    assertTrue(keeps(current = current))
  }

  @Test
  fun `focused model refresh replaces placeholder current change`() {
    val current = ChangeWrapper(Change(fake(file()), fake(file())))
    assertFalse(keeps(current = current))
  }

  @Test
  fun `current change is not kept outside the focused model refresh`() {
    val current = ChangeWrapper(Change(real(file()), real(file())))
    assertFalse(keeps(fromModelRefresh = false, current = current))
    assertFalse(keeps(current = current, selected = current))
    assertFalse(keeps(current = current, keepFocusedFile = false))
    assertFalse(keeps(current = null))
  }

  @Test
  fun `focus is not checked when the cheap conditions fail`() {
    val current = ChangeWrapper(Change(real(file()), real(file())))
    val placeholder = ChangeWrapper(Change(fake(file()), fake(file())))
    val failOnCall = BooleanSupplier { throw AssertionError("focus must not be checked") }
    assertFalse(ChangeViewDiffRequestProcessor.keepsCurrentChange(false, current, null, failOnCall))
    assertFalse(ChangeViewDiffRequestProcessor.keepsCurrentChange(true, current, current, failOnCall))
    assertFalse(ChangeViewDiffRequestProcessor.keepsCurrentChange(true, null, null, failOnCall))
    assertFalse(ChangeViewDiffRequestProcessor.keepsCurrentChange(true, placeholder, null, failOnCall))
  }

  @Test
  fun `restored fake change does not match real changelist change and is replaced`() {
    val file = file()
    val restored = ChangeWrapper(Change(fake(file), fake(file)))
    val refreshed = ChangeWrapper(ChangeListChange(Change(real(file), real(file)), "Changes", "id"))

    // ChangeListChange.HASHING_STRATEGY: a plain Change never equals a ChangeListChange, so refresh finds no match
    assertNotEquals(restored, refreshed)
    assertFalse(keeps(current = restored, selected = null))
  }

  private fun keeps(fromModelRefresh: Boolean = true,
                    current: Wrapper?,
                    selected: Wrapper? = null,
                    keepFocusedFile: Boolean = true): Boolean {
    return ChangeViewDiffRequestProcessor.keepsCurrentChange(fromModelRefresh, current, selected, BooleanSupplier { keepFocusedFile })
  }

  private fun file(): FilePath = LocalFilePath(Path.of("project", "src", "A.txt"), false)

  private fun real(file: FilePath): ContentRevision = CurrentContentRevision(file)

  private fun fake(file: FilePath): ContentRevision = FakeRevision(PROJECT, file, true)

  private companion object {
    // FakeRevision only stores the project; nothing here calls it
    val PROJECT: Project = Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
      throw UnsupportedOperationException(method.name)
    } as Project
  }
}
