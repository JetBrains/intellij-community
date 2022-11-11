// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.expectactual

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.quickfix.TypeAccessibilityChecker
import org.jetbrains.kotlin.idea.util.actualsForExpected
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier

sealed class CreateActualFix<D : KtNamedDeclaration>(
    declaration: D,
    actualModule: Module,
    private val actualPlatform: TargetPlatform,
    generateIt: KtPsiFactory.(Project, TypeAccessibilityChecker, D) -> D?
) : AbstractCreateDeclarationFix<D>(declaration, actualModule, generateIt) {

    override fun getText() = KotlinBundle.message(
        "create.actual.0.for.module.1.2",
        elementType,
        module.name,
        actualPlatform.singleOrNull()?.platformName ?: actualPlatform
    )

    final override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val actualFile = getOrCreateImplementationFile() ?: return
        doGenerate(project, editor, originalFile = file, targetFile = actualFile, targetClass = null)
    }

    override fun findExistingFileToCreateDeclaration(
        originalFile: KtFile,
        originalDeclaration: KtNamedDeclaration
    ): KtFile? {
        for (otherDeclaration in originalFile.declarations) {
            if (otherDeclaration === originalDeclaration) continue
            if (!otherDeclaration.hasExpectModifier()) continue
            val actualDeclaration = otherDeclaration.actualsForExpected(module).singleOrNull() ?: continue
            return actualDeclaration.containingKtFile
        }
        return null
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val d = DiagnosticFactory.cast(diagnostic, Errors.NO_ACTUAL_FOR_EXPECT)
            val declaration = d.psiElement as? KtNamedDeclaration ?: return null
            val compatibility = d.c
            // For function we allow it, because overloads are possible
            if (compatibility.isNotEmpty() && declaration !is KtFunction) return null
            val actualModuleDescriptor = d.b
            val actualModule = (actualModuleDescriptor.getCapability(ModuleInfo.Capability) as? ModuleSourceInfo)?.module ?: return null
            val actualPlatform = actualModuleDescriptor.platform ?: return null
            return when (declaration) {
                is KtClassOrObject -> CreateActualClassFix(declaration, actualModule, actualPlatform)
                is KtFunction, is KtProperty -> CreateActualCallableMemberFix(
                    declaration as KtCallableDeclaration,
                    actualModule,
                    actualPlatform
                )
                else -> null
            }
        }
    }
}

class CreateActualClassFix(
    klass: KtClassOrObject,
    actualModule: Module,
    actualPlatform: TargetPlatform
) : CreateActualFix<KtClassOrObject>(klass, actualModule, actualPlatform, block@{ project, checker, element ->
    checker.findAndApplyExistingClasses(element.collectDeclarationsForAddActualModifier().toList())
    if (!checker.isCorrectAndHaveAccessibleModifiers(element, true)) return@block null

    generateClassOrObject(project, false, element, checker = checker)
})

class CreateActualCallableMemberFix(
    declaration: KtCallableDeclaration,
    actualModule: Module,
    actualPlatform: TargetPlatform
) : CreateActualFix<KtCallableDeclaration>(declaration, actualModule, actualPlatform, block@{ project, checker, element ->
    if (!checker.isCorrectAndHaveAccessibleModifiers(element, true)) return@block null

    val descriptor = element.toDescriptor() as? CallableMemberDescriptor
    descriptor?.let { generateCallable(project, false, element, descriptor, checker = checker) }
})

