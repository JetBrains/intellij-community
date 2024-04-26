// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.testIntegration

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration.AbstractKotlinCreateTestIntention
import org.jetbrains.kotlin.psi.KtClassOrObject

// do not change intention class to be aligned with docs
class KotlinCreateTestIntention: AbstractKotlinCreateTestIntention() {

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun isResolvable(classOrObject: KtClassOrObject): Boolean =
        allowAnalysisOnEdt {
            analyze(classOrObject) {
                classOrObject.getClassOrObjectSymbol() != null
            }
        }

    override fun isApplicableForModule(module: Module): Boolean =
        // TODO: KMP JS case is not applicable
        //
        // TODO: in short, disabled for K2 as far as it has no J2K for it
        //
        // Details: CreateTestIntention relies on JavaTestCreation, all test framework templates are java-based,
        // and entire logic is java-focused. It's way way more easier and reasonable to reuse it and run J2K
        // rather do a copy-cat from java-test-frameworks
        false

    override fun convertJavaClass(
        project: Project,
        generatedClass: PsiClass,
        existingClass: KtClassOrObject?,
        generatedFile: PsiJavaFile,
        srcModule: Module
    ) {
        TODO("Not yet implemented: J2K is required")
    }
}