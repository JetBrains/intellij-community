// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactorings

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter

/**
 * Temp interface to wait for expect/actual support in symbols
 */
open class KotlinSafeDeleteHelper {
    companion object {
        fun getInstance(project: Project): KotlinSafeDeleteHelper = project.service()
    }

    open fun liftToExpected(param : KtParameter) : KtParameter? = null
    open fun runOnExpectAndAllActuals(declaration: KtDeclaration, f: (KtDeclaration) -> Unit) {} 
}