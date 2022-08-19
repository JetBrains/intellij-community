// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.psi.UserDataProperty


internal object CompletionContributorGroupWeigher {
    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<*>? =
            element.groupPriority
    }

    var LookupElement.groupPriority by UserDataProperty(Key<Int>("GROUP_PRIORITY"))


    const val WEIGHER_ID = "kotlin.group.id"
}


