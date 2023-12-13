// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.LanguageExtension
import com.intellij.lang.SmartEnterProcessorWithFixers.Fixer

object LanguageSmartEnterProcessorFixer: LanguageExtension<Fixer<*>>("org.jetbrains.kotlin.smartEnterProcessorFixer")
