// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElementBuilder
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinClassIdSerializer
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinNameSerializer
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableLookupObject
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirSuperEntriesProvider.getSuperClassesAvailableForSuperCall
import org.jetbrains.kotlin.idea.completion.contributors.helpers.SuperCallInsertionHandler
import org.jetbrains.kotlin.idea.completion.contributors.helpers.SuperCallLookupObject
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSuperTypeCallNameReferencePositionContext
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal class K2SuperEntryContributor : K2SimpleCompletionContributor<KotlinSuperTypeCallNameReferencePositionContext>(
    KotlinSuperTypeCallNameReferencePositionContext::class
) {
    context(_: KaSession, context: K2CompletionSectionContext<KotlinSuperTypeCallNameReferencePositionContext>)
    override fun complete() {
        getSuperClassesAvailableForSuperCall(context.positionContext.nameExpression).forEach { superType ->
            val tailText = superType.classId?.asString()?.let { "($it)" }
            LookupElementBuilder.create(SuperLookupObject(superType.name, superType.classId), superType.name.asString())
                .withTailText(tailText)
                .withInsertHandler(SuperCallInsertionHandler)
                .let { addElement(it) }
        }
    }
}

@Serializable
data class SuperLookupObject(
    @Serializable(with = KotlinNameSerializer::class) val className: Name,
    @Serializable(with = KotlinClassIdSerializer::class) val classId: ClassId?,
) : SuperCallLookupObject, SerializableLookupObject {
    override val replaceTo: String
        get() = when {
            classId != null -> "${classId.asSingleFqName().asString()}>"
            else -> "${className.asString()}>"
        }

    override val shortenReferencesInReplaced: Boolean
        get() = classId != null
}
