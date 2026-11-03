// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class CodeInsightTestFixtureTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val myFixture: CodeInsightTestFixture by testFixture {
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val builder = factory.createFixtureBuilder("CodeInsightTestFixtureTest")
    builder.addModule(EmptyModuleFixtureBuilder::class.java)
    val fixture = factory.createCodeInsightFixture(builder.fixture)
    withContext(Dispatchers.EDT) { fixture.setUp() }
    initialized(fixture) {
      withContext(Dispatchers.EDT) { fixture.tearDown() }
    }
  }

  @Test
  fun testAstAccessWhileConfiguringAnotherFile(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      myFixture.configureByText("first.txt", "first")
      val file = myFixture.addFileToProject("second.txt", "sec<caret>ond") as PsiFileImpl
      val document = myFixture.getDocument(file)!!
      assertFalse(file.isContentsLoaded)

      PsiManagerEx.getInstanceEx(myFixture.project).setAssertOnFileLoadingFilter(
        { it != myFixture.file?.virtualFile }, disposable,
      )
      var documentChanged = false
      document.addDocumentListener(object : DocumentListener {
        override fun beforeDocumentChange(event: DocumentEvent) {
          assertNotNull(file.node)
          documentChanged = true
        }
      }, disposable)

      myFixture.configureFromExistingVirtualFile(file.virtualFile)
      assertTrue(documentChanged)
      assertEquals("second", document.text)
      assertEquals(3, myFixture.caretOffset)
    }
  }
}
