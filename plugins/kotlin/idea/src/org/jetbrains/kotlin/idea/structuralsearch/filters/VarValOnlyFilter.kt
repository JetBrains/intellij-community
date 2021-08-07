// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.filters

import com.intellij.structuralsearch.plugin.ui.filters.FilterAction
import com.intellij.structuralsearch.plugin.ui.filters.FilterProvider
import org.jetbrains.kotlin.idea.KotlinBundle

class KotlinFilterProvider : FilterProvider {
    override fun getFilters(): List<FilterAction> = listOf(VarOnlyFilter(), ValOnlyFilter())
}

class VarOnlyFilter : OneStateFilter(
    KotlinBundle.lazyMessage("filter.match.only.vars"),
    KotlinBundle.message("label.match.only.vars"),
    CONSTRAINT_NAME
) {

    companion object {
        const val CONSTRAINT_NAME: String = "kotlinVarOnly"
    }

}

class ValOnlyFilter : OneStateFilter(
    KotlinBundle.lazyMessage("filter.match.only.vals"),
    KotlinBundle.message("label.match.only.vals"),
    CONSTRAINT_NAME
) {

    companion object {
        const val CONSTRAINT_NAME: String = "kotlinValOnly"
    }

}