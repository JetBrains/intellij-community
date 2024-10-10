// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.testIntegration

import com.intellij.codeInsight.navigation.activateFileWithPsiElement
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration.AbstractKotlinCreateTestIntention
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.psi.KtClassOrObject

// do not change intention class to be aligned with docs
class KotlinCreateTestIntention: AbstractKotlinCreateTestIntention() {

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun isResolvable(classOrObject: KtClassOrObject): Boolean =
        allowAnalysisOnEdt {
            analyze(classOrObject) {
                classOrObject.classSymbol != null
            }
        }

    override fun isApplicableForModule(module: Module): Boolean {
        // TODO: KMP JS case is not applicable
        return !module.platform.isJs()
    }

    override fun getTempClassName(project: Project, existingClass: KtClassOrObject): String {
        // no reason for a new temp class name, reuse existed
        return existingClass.name!!
    }

    override fun convertClass(
        project: Project,
        generatedClass: PsiClass,
        existingClass: KtClassOrObject?,
        generatedFile: PsiFile,
        srcModule: Module
    ) {
        project.executeCommand<Unit>(
            KotlinBundle.message("convert.class.0.to.kotlin", generatedClass.name.toString()),
            this
        ) {
            runWriteAction {
                generatedClass.methods.forEach {
                    it.throwsList.referenceElements.forEach { referenceElement -> referenceElement.delete() }
                }
            }

            if (existingClass != null) {
                activateFileWithPsiElement(existingClass)
            } else {
                with(PsiDocumentManager.getInstance(project)) {
                    getDocument(generatedFile)?.let { doPostponedOperationsAndUnblockDocument(it) }
                }
            }
        }
    }
}