// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnusedReceiverParameter")

package org.jetbrains.kotlin.idea.core

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.checkers.OptInNames

/**
 * Experimental and UseExperimental support is removed from compiler completely.
 * Migration and inspection support for those annotations are still needed in IDEA Kotlin plugin.
 */
@K1Deprecation
val OptInNames.OLD_EXPERIMENTAL_FQ_NAME get() = FqName("kotlin.Experimental")
@K1Deprecation
val OptInNames.OLD_USE_EXPERIMENTAL_FQ_NAME get() = FqName("kotlin.UseExperimental")
@K1Deprecation
val OptInNames.OPT_IN_FQ_NAMES get() = setOf(OLD_USE_EXPERIMENTAL_FQ_NAME, OPT_IN_FQ_NAME)
