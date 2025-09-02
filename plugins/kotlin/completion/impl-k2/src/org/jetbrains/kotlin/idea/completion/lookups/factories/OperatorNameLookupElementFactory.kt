// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.idea.completion.implCommon.OperatorNameCompletion
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject
import org.jetbrains.kotlin.name.Name

object OperatorNameLookupElementFactory {
    fun createLookup(operatorName: Name): LookupElementBuilder {
        val lookupElement = LookupElementBuilder.create(OperatorNameLookupObject(operatorName), operatorName.asString())
        return OperatorNameCompletion.decorateLookupElement(lookupElement, operatorName)
    }
}

internal data class OperatorNameLookupObject(
    override val shortName: Name
) : KotlinLookupObject