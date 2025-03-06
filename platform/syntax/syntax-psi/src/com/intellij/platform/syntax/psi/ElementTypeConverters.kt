// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageExtensionWithAny
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ElementTypeConverters {
  @JvmStatic
  val instance: LanguageExtension<ElementTypeConverter> = LanguageExtensionWithAny("com.intellij.syntax.elementTypeConverter")
}