// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight.tooling

import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractJsIdePlatformKindTooling
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.Icon

class FirJsIdePlatformKindTooling : AbstractJsIdePlatformKindTooling() {
    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        // TODO
        return null
    }
}