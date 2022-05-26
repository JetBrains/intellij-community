/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.highlighter

import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinMainRunLineMarkerContributor
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class KotlinFirMainRunLineMarkerContributor : AbstractKotlinMainRunLineMarkerContributor() {
    override fun acceptEntryPoint(function: KtNamedFunction): Boolean {
        // TODO: 04.08.2021 Not all platforms allow main functions, so this check should be more rigid
        return true
    }
}
