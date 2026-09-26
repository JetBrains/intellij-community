// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.extensions

import com.intellij.lang.Language
import com.intellij.platform.syntax.SyntaxLanguage

fun SyntaxLanguage.asIntelliJLanguage(): Language =
  Language.findLanguageByID(this.id) ?: error("Language ${this.id} is not found")