// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.util.registry.Registry
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Experimental


@Experimental
internal class AdDocumentSynchronizer(
  private val adDocument: AdDocument,
  private val coroutineScope: CoroutineScope,
  private val repaintLambda: suspend () -> Unit,
) : PrioritizedDocumentListener, Disposable.Default {

  override fun getPriority(): Int {
    return Integer.MIN_VALUE + 1
  }

  override fun documentChanged(event: DocumentEvent) {
    val realChars = event.document.immutableCharSequence
    coroutineScope.launch {
      if (isDebugMode()) {
        delay(500)
      }
      change {
        shared {
          adDocument.replaceString(
            startOffset = event.offset,
            endOffset = event.offset + event.oldLength,
            chars = event.newFragment,
            modStamp = event.document.modificationStamp,
          )
        }
      }
      val adChars = adDocument.immutableCharSequence
      if (realChars.hashCode() != adChars.hashCode()) {
        assert(realChars.toString() == adChars.toString()) {
          """
              AdDocument is out of sync, expected chars:
              $realChars

              but encountered:
              $adChars
            """.trimIndent()
        }
      }
      repaintLambda.invoke()
    }
  }

  private fun isDebugMode(): Boolean {
    return Registry.`is`("ijpl.rhizome.ad.debug.enabled", false)
  }
}
