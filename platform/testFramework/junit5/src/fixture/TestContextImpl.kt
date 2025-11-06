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
      val displayName = if (context.displayName.startsWith("test") && context.displayName.length > 4) {
        context.displayName.substring("test".length)
      }
      else {
        context.displayName
      }
      val end = displayName.indexOf("(")
      return displayName.substring(0, 1).lowercase(Locale.getDefault()) +
             displayName.substring(1, end)
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