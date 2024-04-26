// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.calls.KtCall
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.idea.highlighting.KotlinCallHighlighterExtension

object KotlinCallHighlighterExtensionForTest : KotlinCallHighlighterExtension {
    private enum class HighlightType(val annotationName: String) {
        SUSPEND("MySuspend"),
        DYNAMIC("MyDynamic"),
        EXTENSION("MyExtension"),
    }

    context(KtAnalysisSession)
    override fun highlightCall(elementToHighlight: PsiElement, call: KtCall): HighlightInfoType? {
        if (call !is KtCallableMemberCall<*, *>) return null
        val highlightType = call.partiallyAppliedSymbol.symbol.annotations.firstNotNullOfOrNull { annotation ->
            HighlightType.values().singleOrNull { it.annotationName == annotation.classId?.shortClassName?.asString() }
        } ?: return null
        return when (highlightType) {
            HighlightType.SUSPEND -> KotlinHighlightInfoTypeSemanticNames.SUSPEND_FUNCTION_CALL
            HighlightType.DYNAMIC -> KotlinHighlightInfoTypeSemanticNames.DYNAMIC_FUNCTION_CALL
            HighlightType.EXTENSION -> KotlinHighlightInfoTypeSemanticNames.EXTENSION_FUNCTION_CALL
        }
    }
}

abstract class AbstractK2HighlightingMetaInfoWithExtensionTest : AbstractK2HighlightingMetaInfoTest() {
    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().extensionArea.getExtensionPoint(KotlinCallHighlighterExtension.EP_NAME)
            .registerExtension(KotlinCallHighlighterExtensionForTest, testRootDisposable)
    }
}