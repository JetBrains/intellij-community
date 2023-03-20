// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

object VariableOrParameterNameWithTypeWeigher {
    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Int = element.nameWithTypePriority
    }

    var LookupElement.nameWithTypePriority by NotNullableUserDataProperty(Key<Int>("NAME_WITH_TYPE_PRIORITY"), 0)

    const val WEIGHER_ID = "kotlin.NameWithTypePriority"
}