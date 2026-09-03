// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.idea.completion.ItemPriority
import org.jetbrains.kotlin.idea.completion.priority

internal object PriorityWeigher : KotlinLookupElementWeigher("kotlin.priority") {

    override fun weigh(element: LookupElement): ItemPriority = element.priority ?: ItemPriority.DEFAULT
}