// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionStatistician
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.statistics.StatisticsInfo
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.KotlinLookupObject
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.AnonymousObjectLookupObject

internal class KotlinCompletionStatistician : CompletionStatistician() {
    override fun serialize(
        element: LookupElement,
        location: CompletionLocation
    ): StatisticsInfo? {
        val o = element.`object` as? KotlinLookupObject ?: return null

        return if (o is AnonymousObjectLookupObject) {
            // Anonymous objects all have the name `object` which would lead to collisions unless
            // we explicitly add the FqName here
            StatisticsInfo("completion#Kotlin#AnonymousObject", o.fqName.asString())
        } else {
            null
        }
    }
}
