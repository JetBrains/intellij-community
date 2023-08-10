// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.formatter

import com.intellij.lang.Language
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.codeStyle.PredefinedCodeStyle

abstract class KotlinPredefinedCodeStyle(@NlsContexts.ListItem name: String, language: Language) : PredefinedCodeStyle(name, language) {
    abstract val codeStyleId: String
}