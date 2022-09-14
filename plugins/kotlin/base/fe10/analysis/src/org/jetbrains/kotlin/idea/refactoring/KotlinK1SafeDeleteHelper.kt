// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import org.jetbrains.kotlin.idea.refactorings.KotlinSafeDeleteHelper
import org.jetbrains.kotlin.idea.util.liftToExpected
import org.jetbrains.kotlin.idea.util.runOnExpectAndAllActuals
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter

class KotlinK1SafeDeleteHelper : KotlinSafeDeleteHelper() {
    override fun liftToExpected(param: KtParameter): KtParameter? {
        return param.liftToExpected()
    }

    override fun runOnExpectAndAllActuals(declaration: KtDeclaration, f: (KtDeclaration) -> Unit) {
        declaration.runOnExpectAndAllActuals(false, false, f)
    }
}