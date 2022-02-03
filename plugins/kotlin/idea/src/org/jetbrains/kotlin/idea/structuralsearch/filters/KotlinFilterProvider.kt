// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.structuralsearch.filters

import com.intellij.structuralsearch.plugin.ui.filters.FilterAction
import com.intellij.structuralsearch.plugin.ui.filters.FilterProvider

class KotlinFilterProvider : FilterProvider {
    override fun getFilters(): List<FilterAction> = listOf(
        AlsoMatchValModifier(),
        AlsoMatchVarModifier(),
        AlsoMatchCompanionObjectModifier()
    )
}