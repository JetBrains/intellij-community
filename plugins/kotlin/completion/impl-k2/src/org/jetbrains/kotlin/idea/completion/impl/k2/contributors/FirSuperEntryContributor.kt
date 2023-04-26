// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirSuperTypeCallNameReferencePositionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirSuperEntriesProvider.getSuperClassesAvailableForSuperCall
import org.jetbrains.kotlin.idea.completion.contributors.helpers.SuperCallLookupObject
import org.jetbrains.kotlin.idea.completion.contributors.helpers.SuperCallInsertionHandler
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name


internal class FirSuperEntryContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<FirSuperTypeCallNameReferencePositionContext>(basicContext, priority) {
    override fun KtAnalysisSession.complete(
        positionContext: FirSuperTypeCallNameReferencePositionContext,
        weighingContext: WeighingContext
    ) = getSuperClassesAvailableForSuperCall(positionContext.nameExpression).forEach { superType ->
        val tailText = superType.classIdIfNonLocal?.asString()?.let { "($it)" }
        LookupElementBuilder.create(SuperLookupObject(superType.name, superType.classIdIfNonLocal), superType.name.asString())
            .withTailText(tailText)
            .withInsertHandler(SuperCallInsertionHandler)
            .let { sink.addElement(it) }
    }
}

private class SuperLookupObject(val className: Name, val classId: ClassId?) : SuperCallLookupObject {
    override val replaceTo: String
        get() = when {
            classId != null -> "${classId.asSingleFqName().asString()}>"
            else -> "${className.asString()}>"
        }

    override val shortenReferencesInReplaced: Boolean
        get() = classId != null
}
