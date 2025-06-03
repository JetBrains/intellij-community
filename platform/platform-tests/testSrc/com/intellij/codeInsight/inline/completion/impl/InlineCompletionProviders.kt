// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.impl

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.*
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class GradualMultiSuggestInlineCompletionProvider(
  private val suggestionBuilder: suspend InlineCompletionSuggestionBuilder.() -> Unit
) : InlineCompletionProvider {

  private val allowedElements = AtomicInteger(0)
  private val currentElement = AtomicInteger(0)
  private val isComputed = AtomicBoolean(false)
  private val computedVariants = AtomicInteger(0)

  suspend fun computeNextElements(number: Int, await: Boolean = true) {
    // Allow to compute [number] elements and wait until they are computed if required
    val limit = allowedElements.addAndGet(number)
    if (await) {
      while (currentElement.get() < limit) {
        yield()
      }
    }
  }

  suspend fun computeNextElement() = computeNextElements(1)

  fun isComputed(): Boolean = isComputed.get()

  override val id: InlineCompletionProviderID = InlineCompletionProviderID("GradualMultiSuggestInlineCompletionProvider")

  override fun isEnabled(event: InlineCompletionEvent): Boolean {
    return event is InlineCompletionEvent.DocumentChange || event is InlineCompletionEvent.DirectCall
  }

  override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
    val suggestion = InlineCompletionSuggestion(suggestionBuilder)
    return object : InlineCompletionSuggestion {
      override suspend fun getVariants(): List<InlineCompletionVariant> {
        val variants = suggestion.getVariants()
        return variants.map { variant ->
          object : InlineCompletionVariant {
            override val data: UserDataHolderBase
              get() = variant.data

            // Waiting before each element
            override val elements: Flow<InlineCompletionElement> = variant.elements
              .onEach {
                while (currentElement.get() >= allowedElements.get()) {
                  yield()
                }
              }.afterEach {
                currentElement.incrementAndGet()
              }.onCompletion {
                if (variants.size == computedVariants.incrementAndGet()) {
                  isComputed.set(true)
                }
              }
          }
        }
      }
    }
  }

  private fun <T> Flow<T>.afterEach(action: suspend (T) -> Unit): Flow<T> = transform {
    emit(it)
    action(it)
  }
}

internal open class SimpleInlineCompletionProvider(val suggestion: List<InlineCompletionElement>) : InlineCompletionProvider {
  override val id: InlineCompletionProviderID = InlineCompletionProviderID("SimpleInlineCompletionProvider")

  override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSingleSuggestion {
    return InlineCompletionSingleSuggestion.build {
      suggestion.forEach { emit(it) }
    }
  }

  override fun isEnabled(event: InlineCompletionEvent): Boolean {
    return event is InlineCompletionEvent.DocumentChange
  }
}

internal class ExceptionInlineCompletionProvider : InlineCompletionProvider {
  override val id: InlineCompletionProviderID = InlineCompletionProviderID("ExceptionInlineCompletionProvider")

  override fun isEnabled(event: InlineCompletionEvent): Boolean = true

  override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
    return object : InlineCompletionSuggestion {
      override suspend fun getVariants(): List<InlineCompletionVariant> {
        throw IllegalStateException("expected error")
      }
    }
  }
}

internal class ExceptionInComputationInlineCompletionProvider : InlineCompletionProvider {
  override val id: InlineCompletionProviderID = InlineCompletionProviderID("ExceptionInVariantInlineCompletionProvider")

  override fun isEnabled(event: InlineCompletionEvent): Boolean = true

  override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
    return InlineCompletionSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("element"))
        throw IllegalStateException("expected error")
      }
    }
  }
}
