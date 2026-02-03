// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal abstract class InlineCompletionTestCase : BasePlatformTestCase() {
  // !!! VERY IMPORTANT !!!
  override fun runInDispatchThread(): Boolean {
    return false
  }

  protected suspend fun InlineCompletionLifecycleTestDSL.assertAllVariants(vararg expected: String) {
    withContext(Dispatchers.EDT) {
      val session = getSession()
      val sizeRange = assertNotNull(session.capture()?.nonEmptyVariantsRange)
      val size = sizeRange.first
      assertEquals(expected.size, size)

      val firstVariant = session.context.textToInsert()
      for (i in 0 until size) {
        assertInlineRender(expected[i])
        nextVariant()
      }
      assertInlineRender(firstVariant)
    }
  }

  protected suspend fun assertSessionSnapshot(nonEmptyVariants: IntRange, activeIndex: Int, vararg variants: ExpectedVariant) {
    val snapshot = withContext(Dispatchers.EDT) {
      val session = getSession()
      assertTrue(session.isActive())
      assertNotNull(session.capture())
    }

    assertEquals(variants.size, snapshot.variants.size)
    assertEquals(variants.size, snapshot.variantsNumber)
    assertEquals(nonEmptyVariants, snapshot.nonEmptyVariantsRange)
    snapshot.variants.withIndex().forEach { (index, actualVariant) ->
      val expectedVariant = variants[index]
      assertEquals(index, actualVariant.index)
      assertEquals(activeIndex == index, actualVariant.isActive)
      assertEquals(expectedVariant.elements, actualVariant.elements.map { it.text })
    }

    val activeVariant = snapshot.activeVariant
    assertSame(snapshot.variants[activeVariant.index], activeVariant)
    assertEquals(activeIndex, activeVariant.index)
  }

  protected fun assertSessionIsNotActive() {
    assertFalse(assertNotNull(InlineCompletionSession.getOrNull (myFixture.editor)).isActive())
  }

  protected suspend fun getVariant(index: Int): InlineCompletionVariant.Snapshot {
    return withContext(Dispatchers.EDT) {
      val session = getSession()
      assertTrue(session.isActive())
      val snapshot = assertNotNull(session.capture())
      snapshot.variants[index]
    }
  }

  protected suspend fun InlineCompletionLifecycleTestDSL.typeChars(chars: String) {
    chars.forEach { typeChar(it) }
  }

  protected fun fillLookup(vararg variants: String) {
    SimpleCompletionContributor.variants = variants.toList()
    val pluginDescriptor = DefaultPluginDescriptor("registerExtension")
    val extension = CompletionContributorEP("any", SimpleCompletionContributor::class.java.name, pluginDescriptor)
    CompletionContributor.EP.point.registerExtension(extension, testRootDisposable)
  }

  protected fun <T : Any> assertNotNull(value: T?): T {
    UsefulTestCase.assertNotNull(value)
    return value!!
  }

  private suspend fun getSession(): InlineCompletionSession {
    return withContext(Dispatchers.EDT) { assertNotNull(InlineCompletionSession.getOrNull(myFixture.editor)) }
  }

  protected class SimpleCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
      variants.forEach { variant ->
        result.addElement(LookupElementBuilder.create(variant))
      }
    }

    companion object {
      var variants = emptyList<String>()
    }
  }

  protected class ExpectedVariant(
    val elements: List<String>,
    val state: InlineCompletionVariant.Snapshot.State
  ) {
    companion object {
      fun untouched() = ExpectedVariant(emptyList(), InlineCompletionVariant.Snapshot.State.UNTOUCHED)

      fun empty() = ExpectedVariant(emptyList(), InlineCompletionVariant.Snapshot.State.COMPUTED)

      fun computed(vararg elements: String) = ExpectedVariant(elements.toList(), InlineCompletionVariant.Snapshot.State.COMPUTED)

      fun inProgress(vararg elements: String) = ExpectedVariant(elements.toList(), InlineCompletionVariant.Snapshot.State.IN_PROGRESS)

      fun invalidated() = ExpectedVariant(emptyList(), InlineCompletionVariant.Snapshot.State.INVALIDATED)
    }
  }
}
