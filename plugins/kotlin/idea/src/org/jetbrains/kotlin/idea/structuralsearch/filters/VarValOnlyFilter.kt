/*
 * Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.structuralsearch.filters

import org.jetbrains.kotlin.idea.KotlinBundle

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