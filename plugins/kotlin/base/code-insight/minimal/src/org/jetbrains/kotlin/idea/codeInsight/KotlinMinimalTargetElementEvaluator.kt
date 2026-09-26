// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtScript

/**
 * Target element evaluator for setups without the Kotlin analysis layer, where files are parsed into
 * [org.jetbrains.kotlin.psi.KtCommonFile] instead of [org.jetbrains.kotlin.psi.KtFile].
 *
 * [KtScript.getName] goes for the script's fully qualified name through the containing [org.jetbrains.kotlin.psi.KtFile]
 * and fails when there is none, so a script must never be picked as the named element under the caret.
 */
class KotlinMinimalTargetElementEvaluator : TargetElementEvaluatorEx2() {
    override fun isAcceptableNamedParent(parent: PsiElement): Boolean = parent !is KtScript
}
