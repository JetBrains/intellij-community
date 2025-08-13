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
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSuperTypeCallNameReferencePositionContext
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal class FirSuperEntryContributor(
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinSuperTypeCallNameReferencePositionContext>(sink, priority) {

    context(KaSession)
    override fun complete(
        positionContext: KotlinSuperTypeCallNameReferencePositionContext,
        weighingContext: WeighingContext,
    ) = getSuperClassesAvailableForSuperCall(positionContext.nameExpression).forEach { superType ->
        val tailText = superType.classId?.asString()?.let { "($it)" }
        LookupElementBuilder.create(SuperLookupObject(superType.name, superType.classId), superType.name.asString())
            .withTailText(tailText)
            .withInsertHandler(SuperCallInsertionHandler)
            .let { sink.addElement(it) }
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
