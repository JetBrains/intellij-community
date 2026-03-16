// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.psi.patternMatching

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.k1DiagnosticsProvider
import org.jetbrains.kotlin.idea.util.psi.patternMatching.KotlinPsiUnifier
import org.jetbrains.kotlin.idea.util.psi.patternMatching.match
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK1PsiUnifierTest : AbstractKotlinPsiUnifierTest() {
    override fun KtElement.getMatches(file: KtFile): List<TextRange> {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(file, DirectiveBasedActionUtils.ERROR_DIRECTIVE, k1DiagnosticsProvider)

        return toRange().match(file, KotlinPsiUnifier.DEFAULT).map { it.range.textRange }
    }
}