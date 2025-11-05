// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.platform.eel.EelApi
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport
import java.util.*
import kotlin.jvm.optionals.getOrNull

internal class TestContextImpl(private val context: ExtensionContext, override val eel: EelApi?) : TestContext {
  override val uniqueId: String
    get() = context.uniqueId

  override val testName: String
    get() {
      val displayName = context.displayName
      val start = 0 // "test".length // TODO: Decide whether to trim "test" prefix if is present
      val end = displayName.indexOf("(")
      return displayName.substring(start, start + 1).lowercase(Locale.getDefault()) +
             displayName.substring(start + 1, end)
    }

  override fun <T : Annotation> findAnnotation(clazz: Class<T>): T? {
    var extContext: ExtensionContext? = context
    while (extContext != null) {
      extContext.element.flatMap { AnnotationSupport.findAnnotation(it, clazz) }.getOrNull()?.let {
        return it
      }
      extContext = extContext.parent.getOrNull()
    }
    return null
  }
}