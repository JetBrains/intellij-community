// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.idea.highlighting.analyzers.KotlinFunctionCallSemanticAnalyzer

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
     * Note that this function is used by [KotlinFunctionCallSemanticAnalyzer]. When this function returns a non-null [HighlightInfoType],
     * [KotlinFunctionCallSemanticAnalyzer] uses it instead of the default one to highlight [call].
     */
    context(KaSession)
    fun highlightCall(elementToHighlight: PsiElement, call: KaCall): HighlightInfoType?

    companion object {
        val EP_NAME: ExtensionPointName<KotlinCallHighlighterExtension> = ExtensionPointName.create("org.jetbrains.kotlin.callHighlighterExtension")
    }
}