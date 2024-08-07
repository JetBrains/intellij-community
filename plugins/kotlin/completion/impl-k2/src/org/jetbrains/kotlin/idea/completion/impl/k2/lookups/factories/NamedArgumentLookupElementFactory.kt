// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.completion.implCommon.handlers.NamedArgumentInsertHandler
import org.jetbrains.kotlin.idea.completion.lookups.CompletionShortNamesRenderer
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance

internal object NamedArgumentLookupElementFactory {

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun createLookup(name: Name, types: List<KaType>): LookupElement {
        val typeText = types.singleOrNull()?.render(CompletionShortNamesRenderer.rendererVerbose, position = Variance.INVARIANT) ?: "..."
        val nameString = name.asString()
        return LookupElementBuilder.create(NamedArgumentLookupObject(name), "$nameString =")
            .withTailText(" $typeText")
            .withIcon(KotlinIcons.PARAMETER)
            .withInsertHandler(NamedArgumentInsertHandler(name))
    }

    fun createLookup(name: Name, value: String): LookupElement {
        return LookupElementBuilder.create(NamedArgumentLookupObject(name), "${name.asString()} = $value")
            .withIcon(KotlinIcons.PARAMETER)
            .withInsertHandler { context, _ ->
                context.document.replaceString(context.startOffset, context.tailOffset, "${name.render()} = $value")
            }
    }
}

internal data class NamedArgumentLookupObject(
    override val shortName: Name,
) : KotlinLookupObject
