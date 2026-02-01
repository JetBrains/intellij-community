// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories

import com.intellij.codeInsight.lookup.LookupElementBuilder
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinNameSerializer
import org.jetbrains.kotlin.idea.completion.implCommon.OperatorNameCompletion
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.KotlinLookupObject
import org.jetbrains.kotlin.name.Name

object OperatorNameLookupElementFactory {
    fun createLookup(operatorName: Name): LookupElementBuilder {
        val lookupElement = LookupElementBuilder.create(OperatorNameLookupObject(operatorName), operatorName.asString())
        return OperatorNameCompletion.decorateLookupElement(lookupElement, operatorName)
    }
}

@Serializable
internal data class OperatorNameLookupObject(
    @Serializable(with = KotlinNameSerializer::class) override val shortName: Name,
) : KotlinLookupObject