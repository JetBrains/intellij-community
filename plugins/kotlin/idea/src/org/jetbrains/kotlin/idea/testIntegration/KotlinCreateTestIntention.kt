// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration

import com.intellij.codeInsight.navigation.activateFileWithPsiElement
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.idea.actions.JavaToKotlinAction
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration.AbstractKotlinCreateTestIntention
import org.jetbrains.kotlin.idea.j2k.j2k
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.j2k.ConverterSettings.Companion.publicByDefault
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinCreateTestIntention: AbstractKotlinCreateTestIntention() {
    override fun isResolvable(classOrObject: KtClassOrObject): Boolean =
        classOrObject.resolveToDescriptorIfAny() != null

    override fun isApplicableForModule(module: Module): Boolean =
        !(module.platform.isJs() && !AdvancedSettings.getBoolean("kotlin.mpp.experimental"))

    override fun convertClass(
        project: Project,
        generatedClass: PsiClass,
        existingClass: KtClassOrObject?,
        generatedFile: PsiFile,
        srcModule: Module
    ) {
        if (generatedFile !is PsiJavaFile || generatedClass.language != JavaLanguage.INSTANCE) return

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
                runWriteAction {
                    val existingMethodNames = existingClass
                        .declarations
                        .asSequence()
                        .filterIsInstance<KtNamedFunction>()
                        .mapTo(HashSet()) { it.name }
                    generatedClass
                        .methods
                        .filter { it.name !in existingMethodNames }
                        .forEach {
                            it.j2k(settings = publicByDefault)
                                ?.let { declaration -> existingClass.addDeclaration(declaration) }
                        }
                    generatedClass.delete()
                }

                activateFileWithPsiElement(existingClass)
            } else {
                with(PsiDocumentManager.getInstance(project)) {
                    getDocument(generatedFile)?.let { doPostponedOperationsAndUnblockDocument(it) }
                }

                JavaToKotlinAction.Handler.convertFiles(
                    listOf(generatedFile),
                    project,
                    srcModule,
                    enableExternalCodeProcessing = false,
                    forceUsingOldJ2k = false,
                    settings = publicByDefault
                ).singleOrNull()
            }
        }
    }

}