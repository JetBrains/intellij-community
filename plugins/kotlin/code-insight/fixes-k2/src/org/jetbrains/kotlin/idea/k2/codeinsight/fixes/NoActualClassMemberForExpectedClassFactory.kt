// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.core.overrideImplement.MemberGenerateMode
import org.jetbrains.kotlin.idea.core.overrideImplement.generateClassWithMembers
import org.jetbrains.kotlin.idea.core.overrideImplement.generateMember
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.*

internal object NoActualClassMemberForExpectedClassFactory {

    val fixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.NoActualClassMemberForExpectedClass ->
        val actualClassOrObject = diagnostic.psi as? KtClassOrObject ?: return@IntentionBased emptyList()
        listOf(AddActualFix(actualClassOrObject, diagnostic.members.mapNotNull { it.first.psi as? KtDeclaration }))
    }
}

private class AddActualFix(
    actualClassOrObject: KtClassOrObject,
    missedDeclarations: List<KtDeclaration>
) : KotlinQuickFixAction<KtClassOrObject>(actualClassOrObject) {
    private val missedDeclarationPointers = missedDeclarations.map { it.createSmartPointer() }

    override fun getFamilyName() = KotlinBundle.message("fix.create.missing.actual.members")

    override fun getText() = familyName

    override fun startInWriteAction(): Boolean = false

    @OptIn(KaExperimentalApi::class)
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val generatedDeclarations = analyzeInModalWindow(element, KotlinBundle.message("fix.change.signature.prepare")) {
            missedDeclarationPointers.mapNotNull { it.element }.mapNotNull { missedDeclaration ->
                val declarationSymbol = missedDeclaration.symbol
                when (missedDeclaration) {
                    is KtClassOrObject ->
                        generateClassWithMembers(project, null, declarationSymbol as KaClassSymbol, element, MemberGenerateMode.ACTUAL)

                    is KtFunction, is KtProperty ->
                        generateMember(project, null, declarationSymbol as KaCallableSymbol, element, false, MemberGenerateMode.ACTUAL)

                    else -> null
                }
            }
        }

        project.executeWriteCommand(familyName, null) {
            for (actualDeclaration in generatedDeclarations) {
                if (actualDeclaration is KtPrimaryConstructor) {
                    if (element.primaryConstructor == null)
                        shortenReferences(element.addAfter(actualDeclaration, element.nameIdentifier) as KtElement)
                } else {
                    shortenReferences(element.addDeclaration(actualDeclaration) as KtElement)
                }
            }
        }
    }
}