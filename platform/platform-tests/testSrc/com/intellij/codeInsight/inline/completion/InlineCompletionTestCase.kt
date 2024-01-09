// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.DefaultPluginDescriptor
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
      val session = checkNotNull(InlineCompletionSession.getOrNull(fixture.editor))
      val sizeRange = checkNotNull(session.estimateNonEmptyVariantsNumber())
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

  protected suspend fun InlineCompletionLifecycleTestDSL.typeChars(chars: String) {
    chars.forEach { typeChar(it) }
  }

  protected fun fillLookup(vararg variants: String) {
    SimpleCompletionContributor.variants = variants.toList()
    val pluginDescriptor = DefaultPluginDescriptor("registerExtension")
    val extension = CompletionContributorEP("any", SimpleCompletionContributor::class.java.name, pluginDescriptor)
    CompletionContributor.EP.point.registerExtension(extension, testRootDisposable)
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
}
