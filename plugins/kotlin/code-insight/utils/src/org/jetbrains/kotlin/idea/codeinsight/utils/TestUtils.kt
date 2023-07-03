// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

fun KtFile.getBooleanTestOption(option: String): Boolean? =
    findDescendantOfType<PsiComment> { it.text.startsWith("// $option:") }
        ?.let { it.text.removePrefix("// $option:").trim().toBoolean() }