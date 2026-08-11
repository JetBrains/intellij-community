// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.classSymbol
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationsRefactoringProcessor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

internal object MoveToSealedMatchingPackageFixFactory {
    val sealedInheritorInDifferentPackage = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.SealedInheritorInDifferentPackage ->
        createQuickFixes(diagnostic.psi)
    }

    val sealedInheritorInDifferentModule = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.SealedInheritorInDifferentModule ->
        createQuickFixes(diagnostic.psi)
    }

    context(_: KaSession)
    private fun createQuickFixes(element: PsiElement): List<IntentionAction> {
        val typeReference = element as? KtTypeReference ?: return emptyList()
        val classSymbol = typeReference.parentOfType<KtClass>()?.classSymbol ?: return emptyList()

        if (classSymbol.superTypes.any { it.expandedSymbol.isBinarySealed() }) {
            return emptyList()
        }

        return listOf(MoveToSealedMatchingPackageQuickFix(typeReference))
    }

    private fun KaClassLikeSymbol?.isBinarySealed(): Boolean {
        return this is KaClassSymbol && modality == KaSymbolModality.SEALED && psi == null
    }
}

private class MoveToSealedMatchingPackageQuickFix(element: KtTypeReference) : KotlinQuickFixAction<KtTypeReference>(element) {
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val typeReference = element ?: return

        val classToMove = typeReference.parentOfType<KtClass>() ?: return

        val targetFile = runWithModalProgressBlocking(typeReference.project, KotlinBundle.message("dialog.progress.collect.members.to.generate")) {
            readAction {
                analyze(typeReference) { typeReference.type.expandedSymbol?.psi?.containingFile as? KtFile }
            }
        } ?: return
        val targetDirectory = targetFile.containingDirectory ?: return
        val className = classToMove.name ?: return
        val targetFileName = "${className}.kt"

        val moveDescriptor = K2MoveDescriptor.Declarations(
            project = project,
            source = K2MoveSourceDescriptor.ElementSource(setOf(classToMove)),
            target = K2MoveTargetDescriptor.File(targetFileName, targetFile.packageFqName, targetDirectory),
        )
        val operationDescriptor = K2MoveOperationDescriptor.Declarations(
            project = project,
            moveDescriptors = listOf(moveDescriptor),
            searchForText = false,
            searchInComments = false,
            searchReferences = true,
            dirStructureMatchesPkg = false,
        )
        val processor = K2MoveDeclarationsRefactoringProcessor(operationDescriptor)
        processor.setPrepareSuccessfulSwingThreadCallback { }
        processor.run()
    }

    override fun startInWriteAction(): Boolean = false

    override fun getText(): String {
        val typeReference = element ?: return ""
        val referencedName = (typeReference.typeElement as? KtUserType)?.referenceExpression?.getReferencedName() ?: return ""

        val classToMove = typeReference.parentOfType<KtClass>() ?: return ""
        return KotlinBundle.message("fix.move.to.sealed.text", classToMove.nameAsSafeName.asString(), referencedName)
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.move.to.sealed.family")
}
