// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor.fixers

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.K1Deprecation

@K1Deprecation
@Deprecated(message = "use org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.isWithCaret", level = DeprecationLevel.ERROR)
fun PsiElement?.isWithCaret(caret: Int) = this?.textRange?.contains(caret) == true
