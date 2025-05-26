// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.OverridesCompletionLookupElementDecorator
import org.jetbrains.kotlin.psi.UserDataProperty

internal object PreferAbstractForOverrideWeigher {
    private const val WEIGHER_ID = "kotlin.preferAbstractForOverride"

    private enum class Weight {
        ABSTRACT_OVERRIDE,
        OPEN_OVERRIDE,
        NO_OVERRIDE,
    }

    context(KaSession)
    fun addWeight(element: OverridesCompletionLookupElementDecorator) {
        element.overrideType = if (element.isImplemented) Weight.ABSTRACT_OVERRIDE else Weight.OPEN_OVERRIDE
    }

    private var LookupElement.overrideType by UserDataProperty(Key<Weight>("KOTLIN_COMPLETION_OVERRIDE_TYPE"))

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<*> = element.overrideType ?: Weight.NO_OVERRIDE
    }
}