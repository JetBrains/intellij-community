// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.Divider
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.SuppressionUtil
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import com.intellij.codeInspection.ex.InspectionProfileWrapper
import com.intellij.codeInspection.util.IntentionName
import com.intellij.concurrency.JobLauncher
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Predicates
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.highlighting.highlighters.isCalleeExpression
import org.jetbrains.kotlin.idea.highlighting.highlighters.isConstructorCallReference
import org.jetbrains.kotlin.idea.inspections.describe
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class KotlinUnusedHighlightingVisitor(private val ktFile: KtFile) {
    private val enabled: Boolean
    private val deadCodeKey: HighlightDisplayKey?
    private val deadCodeInspection: LocalInspectionTool?
    private val deadCodeInfoType: HighlightInfoType.HighlightInfoTypeImpl?
    private val refHolder:KotlinRefsHolder = KotlinRefsHolder()
    private val javaInspection: UnusedDeclarationInspectionBase = UnusedDeclarationInspectionBase()

    init {
        val profile = InspectionProjectProfileManager.getInstance(ktFile.project).getCurrentProfile().let { p ->
            InspectionProfileWrapper.getCustomInspectionProfileWrapper(ktFile)?.apply(p)?.inspectionProfile ?: p
        }
        deadCodeKey = HighlightDisplayKey.find("UnusedSymbol")
        deadCodeInspection = profile.getUnwrappedTool("UnusedSymbol", ktFile) as? LocalInspectionTool

        deadCodeInfoType = if (deadCodeKey == null) null else
            HighlightInfoType.HighlightInfoTypeImpl(
              profile.getErrorLevel(deadCodeKey, ktFile).severity,
                ((profile.getEditorAttributes(deadCodeKey.shortName, ktFile)) ?: HighlightInfoType.UNUSED_SYMBOL.getAttributesKey())
            )
        enabled = deadCodeInspection != null
                  && deadCodeInfoType != null
                  && profile.isToolEnabled(deadCodeKey, ktFile)
                  && HighlightingLevelManager.getInstance(ktFile.project).shouldInspect(ktFile)
    }

    context(KaSession)
    internal fun collectHighlights(holder: HighlightInfoHolder) {
        if (!enabled) return
        Divider.divideInsideAndOutsideAllRoots(ktFile, ktFile.textRange, holder.annotationSession.priorityRange, Predicates.alwaysTrue()) { dividedElements ->
            registerLocalReferences(dividedElements.inside())
            registerLocalReferences(dividedElements.outside())
            // highlight visible symbols first
            collectAndHighlightNamedElements(dividedElements.inside(), holder)
            collectAndHighlightNamedElements(dividedElements.outside(), holder)
            true
        }
    }

    context(KaSession)
    private fun registerLocalReferences(elements: List<PsiElement>) {
        val registerDeclarationAccessVisitor = object : KtVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                if (expression.parent is KtValueArgumentName) {
                    // usage of parameter in form of named argument is not counted
                    return
                }

                val symbol = expression.mainReference.resolveToSymbol()
                if (symbol is KaLocalVariableSymbol || symbol is KaValueParameterSymbol || symbol is KaKotlinPropertySymbol) {
                    refHolder.registerLocalRef(symbol.psi, expression)
                }
                if (!expression.isCalleeExpression()) {
                    val parent = expression.parent

                    if (parent is KtInstanceExpressionWithLabel) {
                        // Do nothing: 'super' and 'this' are highlighted as a keyword
                        return
                    }
                    if (expression.isConstructorCallReference()) {
                        refHolder.registerLocalRef((expression.mainReference.resolveToSymbol() as? KaConstructorSymbol)?.psi, expression)
                    }
                    else if (symbol is KaClassifierSymbol) {
                        refHolder.registerLocalRef(symbol.psi, expression)
                    }
                }
            }

            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                val call = expression.resolveToCall()?.successfulCallOrNull<KaCall>() ?: return
                if (call is KaSimpleFunctionCall) {
                    refHolder.registerLocalRef(call.symbol.psi, expression)
                }
            }

            override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
                val symbol = expression.callableReference.mainReference.resolveToSymbol() ?: return
                refHolder.registerLocalRef(symbol.psi, expression)
            }

            override fun visitCallExpression(expression: KtCallExpression) {
                val callee = expression.calleeExpression ?: return
                val call = expression.resolveToCall()?.singleCallOrNull<KaCall>() ?: return
                if (callee is KtLambdaExpression || callee is KtCallExpression /* KT-16159 */) return
                refHolder.registerLocalRef((call as? KaSimpleFunctionCall)?.symbol?.psi, expression)
            }
        }
        for (declaration in elements) {
            declaration.accept(registerDeclarationAccessVisitor)
        }
    }

    private fun collectAndHighlightNamedElements(psiElements: List<PsiElement>, holder: HighlightInfoHolder) {
        val namedElements: MutableList<KtNamedDeclaration> = mutableListOf()
        val namedElementVisitor = object : KtVisitorVoid() {
            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                namedElements.add(declaration)
            }
        }
        for (declaration in psiElements) {
            declaration.accept(namedElementVisitor)
        }
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(namedElements, ProgressManager.getGlobalProgressIndicator()) { declaration ->
            analyze(declaration) {
                handleDeclaration(declaration, deadCodeInspection!!, deadCodeInfoType!!, deadCodeKey!!, holder)
            }
            true
        }
    }

    context(KaSession)
    private fun handleDeclaration(declaration: KtNamedDeclaration,
                                  deadCodeInspection: LocalInspectionTool,
                                  deadCodeInfoType: HighlightInfoType.HighlightInfoTypeImpl,
                                  deadCodeKey: HighlightDisplayKey,
                                  holder: HighlightInfoHolder) {
        if (!KotlinUnusedSymbolUtil.isApplicableByPsi(declaration)) return
        if (refHolder.isUsedLocally(declaration)) return // even for non-private declarations our refHolder might have usage info
        val mustBeLocallyReferenced = declaration is KtParameter && !(declaration.hasValOrVar()) ||
                                      declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) ||
                                      ((declaration.parent as? KtClassBody)?.parent as? KtClassOrObject)?.isLocal == true
        if (SuppressionUtil.inspectionResultSuppressed(declaration, deadCodeInspection)) {
            return
        }
        val nameIdentifier = declaration.nameIdentifier
        val problemPsiElement =
            if (mustBeLocallyReferenced
                && declaration.annotationEntries.isEmpty() //instead of slow implicit usages checks
                && declaration !is KtClass // look globally for private classes too, since they could be referenced from some fancy .xml
                && (((declaration as? KtParameter)?.parent?.parent as? KtAnnotated)?.annotationEntries?.isEmpty() != false)
        ) {
            nameIdentifier ?: (declaration as? KtConstructor<*>)?.getConstructorKeyword() ?: declaration
        } else {
            KotlinUnusedSymbolUtil.getPsiToReportProblem(declaration, javaInspection)
        }
        if (problemPsiElement == null) return
        val description = declaration.describe() ?: return
        val message = KotlinBaseHighlightingBundle.message("inspection.message.never.used", description)
        val builder = UnusedSymbolUtil.createUnusedSymbolInfoBuilder(problemPsiElement, message, deadCodeInfoType, null)
        val fixes = KotlinUnusedSymbolUtil.createQuickFixes(declaration)
        fixes.forEach { builder.registerFix(it, null, null, null, deadCodeKey) }
        holder.add(builder.create())
    }
}

class KotlinRefsHolder {
    private val localRefs = mutableMapOf<KtDeclaration, KtElement>()

    fun registerLocalRef(declaration: PsiElement?, reference: KtElement) {
        if (declaration is KtDeclaration) {
            localRefs[declaration] = reference
        }
    }

    fun isUsedLocally(declaration: KtDeclaration): Boolean {
        if (localRefs.containsKey(declaration)) {
            return true
        }

        if (declaration is KtClass) {
            return declaration.primaryConstructor?.let { localRefs.containsKey(it) } == true ||
                    declaration.secondaryConstructors.any { localRefs.containsKey(it) }
        }
        return false
    }
}

class SafeDeleteFix(declaration: KtNamedDeclaration) : LocalQuickFixAndIntentionActionOnPsiElement(declaration) {
    @Nls
    private val name: String =
        KotlinBaseHighlightingBundle.message(declaration.toNameKey(), declaration.name ?: declaration.text)

    private fun KtNamedDeclaration.toNameKey(): String =
        when (this) {
            is KtPrimaryConstructor -> "safe.delete.primary.ctor.text.0"
            is KtSecondaryConstructor -> "safe.delete.secondary.ctor.text.0"
            is KtParameter -> "safe.delete.parameter.text.0"
            else -> "safe.delete.text.0"
        }

    override fun getText(): @IntentionName String {
       return name
    }

    override fun getFamilyName(): String = KotlinBaseHighlightingBundle.message("safe.delete.family")

    override fun startInWriteAction(): Boolean = false

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val element = startElement as? KtDeclaration ?: return

        SafeDeleteHandler.invoke(project, arrayOf(element), false)
    }
}