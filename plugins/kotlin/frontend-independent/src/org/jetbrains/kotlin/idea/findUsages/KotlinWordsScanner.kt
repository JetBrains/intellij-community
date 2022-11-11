// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.findUsages

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens.*

class KotlinWordsScanner : DefaultWordsScanner(
    KotlinLexer(),
    TokenSet.create(IDENTIFIER).also { KEYWORDS },
    COMMENTS,
    TokenSet.create(INTEGER_LITERAL, CHARACTER_LITERAL, FLOAT_LITERAL, NULL_KEYWORD, TRUE_KEYWORD, FALSE_KEYWORD).also { STRINGS }
)