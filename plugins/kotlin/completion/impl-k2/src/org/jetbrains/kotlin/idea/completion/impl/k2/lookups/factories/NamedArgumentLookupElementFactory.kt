// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinNameSerializer
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.implCommon.handlers.NamedArgumentInsertHandler
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.renderVerbose
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.render

internal object NamedArgumentLookupElementFactory {

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun createLookup(name: Name, types: List<KaType>): LookupElement {
        val typeText = types.singleOrNull()?.renderVerbose() ?: "..."
        val nameString = name.asString()
        return LookupElementBuilder.create(NamedArgumentLookupObject(name), "$nameString =")
            .withTailText(" $typeText")
            .withIcon(KotlinIcons.PARAMETER)
            .withInsertHandler(NamedArgumentInsertHandler(name))
    }

    fun createLookup(name: Name, value: String): LookupElement {
        return LookupElementBuilder.create(NamedArgumentLookupObject(name), "${name.asString()} = $value")
            .withIcon(KotlinIcons.PARAMETER)
            .withInsertHandler(NamedArgumentWithValueInsertionHandler(name, value))
    }
}

@Serializable
internal data class NamedArgumentWithValueInsertionHandler(
    @Serializable(with = KotlinNameSerializer::class) private val name: Name,
    val value: String,
) : SerializableInsertHandler {
    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement
    ) {
        context.document.replaceString(context.startOffset, context.tailOffset, "${name.render()} = $value")
    }
}

@Serializable
internal data class NamedArgumentLookupObject(
    @Serializable(with = KotlinNameSerializer::class) override val shortName: Name,
) : KotlinLookupObject
