// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.KotlinTrySurrounderBase
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtTryExpression

sealed class KotlinTryExpressionSurrounder : KotlinControlFlowExpressionSurrounderBase() {
    class TryCatch : KotlinTryExpressionSurrounder() {
        @NlsSafe
        override fun getTemplateDescription() = "try { expr } catch {}"
        override fun getPattern() = "try { $0 } catch (e: Exception) { TODO(\"Not yet implemented\") }"
    }

    class TryCatchFinally : KotlinTryExpressionSurrounder() {
        @NlsSafe
        override fun getTemplateDescription() = "try { expr } catch {} finally {}"
        override fun getPattern() = "try { $0 } catch (e: Exception) { TODO(\"Not yet implemented\") } finally {}"

    }

    override fun getRange(editor: Editor, replaced: KtExpression): TextRange? {
        val tryExpression = replaced as KtTryExpression
        return KotlinTrySurrounderBase.getCatchTypeParameterTextRange(tryExpression)
    }
}

