// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.KotlinTrySurrounderBase
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtTryExpression

sealed class KotlinTryExpressionSurrounder : KotlinControlFlowExpressionSurrounderBase() {
    class TryCatch : KotlinTryExpressionSurrounder() {
        override fun getTemplateDescription() = "try { expr } catch {}"
        override fun getPattern() = "try { $0 } catch (e: Exception) {}"
    }

    class TryCatchFinally : KotlinTryExpressionSurrounder() {
        override fun getTemplateDescription() = "try { expr } catch {} finally {}"
        override fun getPattern() = "try { $0 } catch (e: Exception) {} finally {}"
    }

    override fun getRange(editor: Editor, replaced: KtExpression): TextRange? {
        val tryExpression = replaced as KtTryExpression
        return KotlinTrySurrounderBase.getCatchTypeParameterTextRange(tryExpression)
    }
}

