// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.simple
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.scopes.memberScope
import org.jetbrains.kotlin.analysis.api.scopes.staticMemberScope
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.fullyExpandedType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.quickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.codeinsight.intentions.branchedTransformations.isPure
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationsRefactoringProcessor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isFromCompanionBlock
import org.jetbrains.kotlin.resolution.KtResolvableCall

internal object CompanionMemberFixFactories {

    val unresolvedReferenceFactory: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.UnresolvedReference> =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedReference ->
            createFixes(diagnostic.psi)
        }

    val ofOverloadsInBlockAndObjectFactory = quickFixesPsiBasedFactory<PsiElement> { psiElement ->
        val declaration = psiElement.getNonStrictParentOfType<KtNamedDeclaration>() ?: return@quickFixesPsiBasedFactory emptyList()
        listOfNotNull(createMoveOverloadFix(declaration))
    }

    val inapplicableOperatorModifierFactory = quickFixesPsiBasedFactory<PsiElement> { psiElement ->
        val function = psiElement.getNonStrictParentOfType<KtNamedFunction>()
        if (function?.isFromCompanionBlock != true) return@quickFixesPsiBasedFactory emptyList()
        listOf(MoveToCompanionObjectFix(function))
    }

    private fun createMoveOverloadFix(declaration: KtNamedDeclaration): MoveToCompanionFix? =
        when {
            declaration.isFromCompanionBlock -> MoveToCompanionObjectFix(declaration)
            (declaration.containingClassOrObject as? KtObjectDeclaration)?.isCompanion() == true -> MoveToCompanionBlockFix(declaration)
            else -> return null
        }

    context(_: KaSession)
    private fun createFixes(diagnosticPsi: PsiElement): List<ModCommandAction> {
        val reference = findReferenceExpression(diagnosticPsi) ?: return emptyList()

        val qualified = reference.getQualifiedElement() as? KtQualifiedExpression ?: return emptyList()
        // do not suggest when side effects possible
        if (!isReceiverPure(qualified)) return emptyList()

        val receiverType = reference.getReceiverExpression()?.expressionType?.fullyExpandedType ?: return emptyList()
        val receiverClass = receiverType.expandedSymbol as? KaNamedClassSymbol ?: return emptyList()
        val classId = receiverClass.classId ?: return emptyList()

        val name = reference.getReferencedNameAsName()
        val targets = receiverClass.findCompanionDeclarations(name, receiverType, qualified)
        if (targets.isEmpty()) return emptyList()

        val selectorText = qualified.selectorExpression?.text ?: return emptyList()
        val fqClassName = classId.asSingleFqName().asString()

        return buildList {
            if (resolvesTo(qualified, selectorText, targets)) {
                add(RemoveInstanceReceiverForCompanionMemberFix(qualified))
            } else if (resolvesTo(qualified, "$fqClassName.$selectorText", targets)) {
                add(ReplaceInstanceReceiverWithClassNameFix(qualified, fqClassName))
            }
        }
    }

    private fun findReferenceExpression(diagnosticPsi: PsiElement): KtSimpleNameExpression? {
        val reference = when (diagnosticPsi) {
            is KtSimpleNameExpression -> diagnosticPsi
            is KtCallExpression -> diagnosticPsi.calleeExpression as? KtSimpleNameExpression
            is KtQualifiedExpression -> findReferenceExpression(diagnosticPsi.selectorExpression ?: return null)

            else -> null
        } ?: return null

        if (reference.parent is KtCallableReferenceExpression) return null
        return reference
    }

    // companion can appear in a form of companion object, companion block or companion extension
    // the latter are only possible when corresponding language feature is enabled
    context(_: KaSession)
    private fun KaNamedClassSymbol.findCompanionDeclarations(
        name: Name,
        receiverType: KaType,
        qualified: KtQualifiedExpression,
    ): Set<KtCallableDeclaration> {
        val languageVersionSettings = qualified.languageVersionSettings

        val objectMembers = companionObject?.memberScope?.callables(name).orEmpty()

        val blockMembers = if (languageVersionSettings.supportsFeature(LanguageFeature.CompanionBlocks)) {
            staticMemberScope.callables(name)
                .filter { (it.psi as? KtCallableDeclaration)?.isFromCompanionBlock == true }
        } else {
            emptySequence()
        }

        val extensions = if (languageVersionSettings.supportsFeature(LanguageFeature.CompanionExtensions)) {
            KtSymbolFromIndexProvider(qualified.containingKtFile).getExtensionCallableSymbolsByName(
                name = name,
                receiverTypes = listOf(receiverType),
                psiFilter = { it.hasModifier(KtTokens.COMPANION_KEYWORD) },
            )
        } else {
            emptySequence()
        }

        return (objectMembers + blockMembers + extensions)
            .filter { it.isVisible(qualified) }
            .mapNotNullTo(mutableSetOf()) { it.psi as? KtCallableDeclaration }
    }

    private fun isReceiverPure(qualified: KtQualifiedExpression): Boolean =
        qualified.receiverExpression.safeDeparenthesize().let { it is KtThisExpression || it.isPure() }

    private fun resolvesTo(
        qualified: KtQualifiedExpression,
        expressionText: String,
        targets: Set<KtCallableDeclaration>,
    ): Boolean {
        val fragment = KtPsiFactory.contextual(qualified).createExpressionCodeFragment(expressionText, context = qualified)
        val content = fragment.getContentElement() as? KtResolvableCall ?: return false
        return analyze(content) {
            content.resolveSuccessfulCall()?.simple?.symbol?.psi in targets
        }
    }
}

private sealed class MoveToCompanionFix(
    declaration: KtNamedDeclaration
) : KotlinQuickFixAction<KtNamedDeclaration>(declaration) {
    protected abstract val actionName: @IntentionName String

    protected abstract fun getTarget(declaration: KtNamedDeclaration): K2MoveTargetDescriptor.Declaration<*>?

    override fun getText(): @IntentionName String = actionName

    override fun getFamilyName(): @IntentionFamilyName String = actionName

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        val declaration = element ?: return false
        return getTarget(declaration) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val declaration = element ?: return
        val target = getTarget(declaration) ?: return
        val moveDescriptor = K2MoveDescriptor.Declarations(
            project = project,
            source = K2MoveSourceDescriptor.ElementSource(setOf(declaration)),
            target = target,
        )
        val descriptor = K2MoveOperationDescriptor.Declarations(
            project = project,
            moveDescriptors = listOf(moveDescriptor),
            searchForText = false,
            searchInComments = false,
            searchReferences = true,
            dirStructureMatchesPkg = false,
        )
        val processor = object : K2MoveDeclarationsRefactoringProcessor(descriptor) {
            override fun openFilesAfterMoving(movedElements: List<SmartPsiElementPointer<KtNamedDeclaration>>) {
                movedElements.singleOrNull()?.element?.takeIf { it.isValid }?.let { movedElement ->
                    editor?.takeIf { !it.isDisposed }?.caretModel?.moveToOffset(movedElement.startOffset)
                }
            }
        }
        processor.setPrepareSuccessfulSwingThreadCallback { }
        processor.run()
    }

    override fun startInWriteAction(): Boolean = false
}

private class MoveToCompanionObjectFix(
    declaration: KtNamedDeclaration
) : MoveToCompanionFix(declaration) {
    override val actionName: String
        get() = KotlinBundle.message("move.to.companion.object")

    override fun getTarget(declaration: KtNamedDeclaration): K2MoveTargetDescriptor.Declaration<*>? {
        val containingClass = declaration.containingClassOrObject as? KtClass ?: return null
        return K2MoveTargetDescriptor.CompanionObject(containingClass)
    }
}

private class MoveToCompanionBlockFix(
    declaration: KtNamedDeclaration
) : MoveToCompanionFix(declaration) {
    override val actionName: String
        get() = KotlinBundle.message("move.to.companion.block")

    override fun getTarget(declaration: KtNamedDeclaration): K2MoveTargetDescriptor.Declaration<*>? {
        val companionObject = declaration.containingClassOrObject as? KtObjectDeclaration ?: return null
        if (!companionObject.isCompanion()) return null
        val containingClass = companionObject.containingClass() ?: return null
        return K2MoveTargetDescriptor.CompanionBlock(containingClass)
    }
}

class ReplaceInstanceReceiverWithClassNameFix(
    element: KtQualifiedExpression,
    private val fqClassName: String,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtQualifiedExpression, String>(element, fqClassName) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtQualifiedExpression,
        elementContext: String,
        updater: ModPsiUpdater,
    ) {
        val selectorText = element.selectorExpression?.text ?: return
        val replacement = KtPsiFactory(actionContext.project).createExpression("$elementContext.$selectorText")
        shortenReferences(
            element = element.replaced(replacement),
            callableShortenStrategy = { ShortenStrategy.DO_NOT_SHORTEN },
        )
    }

    override fun getActionPresentation(context: ActionContext, element: KtQualifiedExpression): Presentation =
        Presentation.of(
            KotlinBundle.message("fix.companion.member.replace.receiver.text", fqClassName.substringAfterLast('.'))
        )

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.companion.member.replace.receiver.family")
}

class RemoveInstanceReceiverForCompanionMemberFix(
    element: KtQualifiedExpression,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtQualifiedExpression>(element) {

    override fun invoke(context: ActionContext, element: KtQualifiedExpression, updater: ModPsiUpdater) {
        element.replace(element.selectorExpression ?: return)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.companion.member.remove.receiver.family")
}
