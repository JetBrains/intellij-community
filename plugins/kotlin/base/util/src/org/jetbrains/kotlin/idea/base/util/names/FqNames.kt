// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util.names

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.checkers.OptInNames

@Suppress("MemberVisibilityCanBePrivate")
object FqNames {
    object OptInFqNames {
        val OLD_EXPERIMENTAL_FQ_NAME = FqName("kotlin.Experimental")
        val OLD_USE_EXPERIMENTAL_FQ_NAME = FqName("kotlin.UseExperimental")

        val OPT_IN_FQ_NAMES = setOf(OLD_USE_EXPERIMENTAL_FQ_NAME, OptInNames.OPT_IN_FQ_NAME)
    }
}