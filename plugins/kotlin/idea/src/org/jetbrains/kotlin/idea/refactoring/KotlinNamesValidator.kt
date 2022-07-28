// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier

class KotlinNamesValidator : NamesValidator {
    private val KEYWORD_SET = KtTokens.KEYWORDS.types.filterIsInstance<KtKeywordToken>().map { it.value }.toSet()

    override fun isKeyword(name: String, project: Project?): Boolean = name in KEYWORD_SET
    override fun isIdentifier(name: String, project: Project?): Boolean = name.isIdentifier()
}
