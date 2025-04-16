// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime

import com.intellij.platform.syntax.i18n.ResourceBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import kotlin.jvm.JvmStatic

object SyntaxRuntimeBundle {
  private const val BUNDLE: @NonNls String = "messages.SyntaxRuntimeBundle"
    
  private val bundle = ResourceBundle("com.intellij.platform.syntax.runtime.SyntaxRuntimeBundle", BUNDLE, this, emptyMap())
  
  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return bundle.message(key, *params)
  }
  
  @JvmStatic
  fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): () -> @Nls String {
    return bundle.messagePointer(key, *params)
  }
}