// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.psi.extensions

import com.intellij.lang.Language
import com.intellij.platform.syntax.extensions.SyntaxLanguage
import org.jetbrains.annotations.ApiStatus

fun SyntaxLanguage.asIntelliJLanguage(): Language =
  Language.findLanguageByID(this.id) ?: error("Language ${this.id} is not found")