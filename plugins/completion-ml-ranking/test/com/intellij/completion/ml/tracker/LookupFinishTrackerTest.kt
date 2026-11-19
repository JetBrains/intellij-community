// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.tracker

import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class LookupFinishTrackerTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture()
  }

  private val completion by lightCompletionFixture(projectFixture)
  private val myFixture get() = completion.fixture
  private val lookup get() = completion.getLookup()!!

  private val listener = TestFinishListener()

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    setupCompletionContext(myFixture)
    myFixture.completeBasic()
    lookup.addLookupListener(listener)
  }

  @Test
  fun `test cancelled`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    lookup.hide()
    assertFinishType(FinishType.CANCELLED)
  }

  @Test
  fun `test explicit`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    assertFinishType(FinishType.EXPLICIT)
  }

  @Test
  fun `test typed`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.type('r')
    myFixture.type('u')
    myFixture.type('n')
    lookup.hide()
    assertFinishType(FinishType.TYPED)
  }

  private fun assertFinishType(type: FinishType) {
    assertEquals(type, listener.finishType)
  }

  private class TestFinishListener : LookupFinishListener() {
    var finishType: FinishType = FinishType.UNKNOWN

    override fun cancelled(lookup: LookupImpl, canceledExplicitly: Boolean) = update(FinishType.CANCELLED)
    override fun explicitSelect(lookup: LookupImpl, element: LookupElement) = update(FinishType.EXPLICIT)
    override fun typedSelect(lookup: LookupImpl, element: LookupElement) = update(FinishType.TYPED)

    private fun update(type: FinishType) {
      assertEquals(finishType, FinishType.UNKNOWN)
      finishType = type
    }
  }

  private enum class FinishType {
    UNKNOWN, TYPED, CANCELLED, EXPLICIT
  }
}