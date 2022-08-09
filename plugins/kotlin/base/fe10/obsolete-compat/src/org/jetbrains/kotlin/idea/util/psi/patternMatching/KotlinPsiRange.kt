// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.util.psi.patternMatching

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange as toRangeNew

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.psi.unifier.toRange' instead.",
    ReplaceWith("toRange(significantOnly)", "org.jetbrains.kotlin.idea.base.psi.unifier.toRange")
)
fun List<PsiElement>.toRange(significantOnly: Boolean = true): KotlinPsiRange = toRangeNew(significantOnly)

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.psi.unifier.toRange' instead.",
    ReplaceWith("toRange()", "org.jetbrains.kotlin.idea.base.psi.unifier.toRange")
)
fun PsiElement?.toRange(): KotlinPsiRange = toRangeNew()