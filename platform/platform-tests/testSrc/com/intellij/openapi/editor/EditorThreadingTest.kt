// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.impl.EditorThreading
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows

@TestApplication
class EditorThreadingTest {

  private companion object {
    val project = projectFixture()
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
    val file = sourceRoot.psiFileFixture("file.txt", "abcde")
  }

  private val editor = file.editorFixture()


  val actions = listOf(
    { editor.get().caretModel.offset } to "caret",
    { EditorThreading.assertInteractionAllowed() } to "raw threading",
    { editor.get().selectionModel.selectedText } to "selection",
    { editor.get().foldingModel.allFoldRegions } to "folding model",
  )

  fun runTest(processor: (() -> Unit) -> Unit): List<DynamicTest> {
    return actions.map {
      DynamicTest.dynamicTest(it.second) {
        processor { it.first() }
      }
    }
  }

  private fun withRawAccess(kind: Boolean, action: () -> Unit) {
    val registryValue = Registry.get("editor.allow.raw.access.on.edt")
    val current = registryValue.asBoolean()
    registryValue.setValue(kind)
    try {
      action
    }
    finally {
      registryValue.setValue(current)
    }
  }

  @TestFactory
  fun `access to editor is not allowed on BGT`() = runTest { action ->
    withRawAccess(false) {
      timeoutRunBlocking(context = Dispatchers.Default) {
        assertThrows<Exception> {
          action()
        }
      }
    }
  }

  @TestFactory
  fun `access to editor is not allowed on raw EDT`() = runTest { action ->
    withRawAccess(true) {
      timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
        assertThrows<Exception> {
          action()
        }
      }
    }
  }

  @TestFactory
  fun `access to editor is allowed under read lock`() = runTest { action ->
    withRawAccess(false) {
      timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
        readAction {
          action()
        }
      }
    }
  }

  @TestFactory
  fun `access to editor is allowed on raw EDT with flag`() = runTest { action ->
    withRawAccess(true) {
      timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
        action()
      }
    }
  }

  @TestFactory
  @RegistryKey("editor.allow.raw.access.on.edt", "true")
  fun `access to editor is allowed under read lock with flag`() = runTest { action ->
    withRawAccess(true) {
      timeoutRunBlocking(context = Dispatchers.Default) {
        readAction {
          action()
        }
      }
    }
  }
}