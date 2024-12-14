// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

internal object TrailingLambdaParameterNameWeigher : LookupElementWeigher(
    /* id = */ "kotlin.isTrailingLambdaParameter",
    /* negated = */ true,
    /* dependsOnPrefix = */ false,
) {

    internal var LookupElement.isTrailingLambdaParameter: Boolean by NotNullableUserDataProperty(
        key = Key.create("kotlin.lookupElement.isTrailingLambdaParameter"),
        defaultValue = false,
    )

    override fun weigh(element: LookupElement): Comparable<Boolean> =
        element.isTrailingLambdaParameter
}