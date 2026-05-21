// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionStatistician
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.statistics.StatisticsInfo
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.KotlinLookupObject
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.AnonymousObjectLookupObject

/**
 * This statistician is responsible for providing [StatisticsInfo] instances for certain Kotlin completion
 * lookup items.
 * This is required in case the itemText of the lookup item is not enough to distinguish it from other
 * similar lookup items so they would all share the same statistics.
 * For example, all anonymous object lookup items have the itemText `object`, so their statistics would collide
 * without this statistician.
 */
internal class KotlinCompletionStatistician : CompletionStatistician() {
    override fun serialize(
        element: LookupElement,
        location: CompletionLocation
    ): StatisticsInfo? {
        val lookupObject = element.`object` as? KotlinLookupObject ?: return null

        return if (lookupObject is AnonymousObjectLookupObject) {
            // Anonymous objects all have the name `object` which would lead to collisions unless
            // we explicitly add the FqName here
            StatisticsInfo("completion#Kotlin#AnonymousObject", lookupObject.fqName.asString())
        } else {
            null
        }
    }
}
