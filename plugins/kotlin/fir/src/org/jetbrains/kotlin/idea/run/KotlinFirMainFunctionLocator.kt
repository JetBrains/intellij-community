/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.run

import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class KotlinFirMainFunctionLocatingService : KotlinMainFunctionLocatingService {
    override fun isMain(function: KtNamedFunction): Boolean {
        // TODO: 04.08.2021 make checks more exhaustive
        return function.name == "main" &&
                function.valueParameters.size < 2
    }

    override fun hasMain(declarations: List<KtDeclaration>): Boolean =
        declarations.any { it is KtNamedFunction && isMain(it) }
}
