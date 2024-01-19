// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCall
import org.jetbrains.kotlin.idea.highlighting.highlighters.FunctionCallHighlighter

/**
 * An interface for an extension point that enables a user-defined call highlighter.
 *
 * To add a new custom call highlighter, you have to add
 *
 *   <extensions defaultExtensionNs="org.jetbrains.kotlin"><callHighlighterExtension implementation="..."/></extensions>
 *
 * in XML. The "implementation" must be a class that implements this interface. See highlighting-k2.xml.
 */
interface KotlinCallHighlighterExtension {
    /**
     * A function determines how to highlight [call] for PSI [elementToHighlight].
     *
     * Note that this function is used by [FunctionCallHighlighter]. When this function returns a non-null [HighlightInfoType],
     * [FunctionCallHighlighter] uses it instead of the default one to highlight [call].
     */
    context(KtAnalysisSession)
    fun highlightCall(elementToHighlight: PsiElement, call: KtCall): HighlightInfoType?

    companion object {
        val EP_NAME: ExtensionPointName<KotlinCallHighlighterExtension> = ExtensionPointName.create("org.jetbrains.kotlin.callHighlighterExtension")
    }
}