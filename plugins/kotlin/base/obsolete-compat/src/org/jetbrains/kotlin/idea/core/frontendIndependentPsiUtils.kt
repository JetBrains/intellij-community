// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core

import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier

@Deprecated(
    "Use 'unquoteKotlinIdentifier()' instead",
    ReplaceWith("unquoteKotlinIdentifier()", "org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier")
)
fun String.unquote(): String = unquoteKotlinIdentifier()