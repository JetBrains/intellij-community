// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.keywords

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.idea.completion.KeywordLookupObject
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirSuperEntriesProvider.getSuperClassesAvailableForSuperCall
import org.jetbrains.kotlin.idea.completion.contributors.helpers.SuperCallLookupObject
import org.jetbrains.kotlin.idea.completion.contributors.helpers.SuperCallInsertionHandler
import org.jetbrains.kotlin.idea.completion.createKeywordElement
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandler
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinClassIdSerializer
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinNameSerializer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression

internal object SuperKeywordHandler : CompletionKeywordHandler<KaSession>(KtTokens.SUPER_KEYWORD) {
    context(KaSession)
    override fun createLookups(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ): Collection<LookupElement> {
        val superClasses = getSuperClassesAvailableForSuperCall(parameters.position)

        if (superClasses.isEmpty()) {
            return emptyList()
        }

        if (expression == null) {
            // for completion in secondary constructor delegation call
            return listOf(lookup)
        }

        return when {
            superClasses.size <= 1 -> listOf(lookup)
            else -> buildList {
                add(lookup)
                superClasses.mapTo(this) { symbol ->
                    createKeywordElement("super", "<${symbol.name}>", SuperKeywordLookupObject(symbol.name, symbol.classId))
                        .withInsertHandler(SuperCallInsertionHandler)
                }
            }
        }
    }
}

@Serializable
internal class SuperKeywordLookupObject(
    @Serializable(with = KotlinNameSerializer::class) val className: Name,
    @Serializable(with = KotlinClassIdSerializer::class) val classId: ClassId?
) : KeywordLookupObject(), SuperCallLookupObject {
    override val replaceTo: String?
        get() = classId?.let { "super<${it.asSingleFqName().asString()}>" }

    override val shortenReferencesInReplaced: Boolean
        get() = classId != null
}

