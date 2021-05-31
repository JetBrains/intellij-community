// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

internal fun createStarTypeArgumentsList(typeArgumentsCount: Int): String =
    if (typeArgumentsCount > 0) {
        List(typeArgumentsCount) { "*" }.joinToString(prefix = "<", postfix = ">")
    } else {
        ""
    }