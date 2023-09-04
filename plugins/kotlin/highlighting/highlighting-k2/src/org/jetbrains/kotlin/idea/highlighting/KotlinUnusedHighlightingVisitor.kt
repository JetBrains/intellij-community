// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.codeInspection.ex.InspectionProfileWrapper
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Predicates
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.inspections.describe
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class KotlinUnusedHighlightingVisitor(private val ktFile: KtFile, private val refHolder: KotlinRefsHolder) {
    internal fun collectHighlights(holder: HighlightInfoHolder) {
        val profile = InspectionProjectProfileManager.getInstance(ktFile.project).getCurrentProfile().let { p ->
            InspectionProfileWrapper.getCustomInspectionProfileWrapper(ktFile)?.apply(p)?.inspectionProfile ?: p
        }

        val deadCodeKey = HighlightDisplayKey.find("UnusedSymbol") ?: return
        val deadCodeInspection = profile.getUnwrappedTool("UnusedSymbol", ktFile) as? LocalInspectionTool ?: return

        val deadCodeInfoType =
            HighlightInfoType.HighlightInfoTypeImpl(
              profile.getErrorLevel(deadCodeKey, ktFile).severity,
                ((profile.getEditorAttributes(deadCodeKey.toString(), ktFile)) ?: HighlightInfoType.UNUSED_SYMBOL.getAttributesKey())
            )
        if (!profile.isToolEnabled(deadCodeKey, ktFile)) return
        if (!HighlightingLevelManager.getInstance(ktFile.project).shouldInspect(ktFile)) return

        Divider.divideInsideAndOutsideAllRoots(ktFile, ktFile.textRange, holder.annotationSession.priorityRange, Predicates.alwaysTrue()) { dividedElements ->
            analyze(ktFile) {
                val declarationVisitor = object : KtVisitorVoid() {
                    override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                        handleDeclaration(declaration, deadCodeInspection, deadCodeInfoType, deadCodeKey, holder)
                    }
                }
                // highlight visible symbols first
                for (declaration in dividedElements.inside()) {
                    declaration.accept(declarationVisitor)
                }
                for (declaration in dividedElements.outside()) {
                    declaration.accept(declarationVisitor)
                }
            }
            true
        }
    }

    context(KtAnalysisSession)
    private fun handleDeclaration(declaration: KtNamedDeclaration,
                                  deadCodeInspection: LocalInspectionTool,
                                  deadCodeInfoType: HighlightInfoType.HighlightInfoTypeImpl,
                                  deadCodeKey: HighlightDisplayKey,
                                  holder: HighlightInfoHolder) {
        if (!KotlinUnusedSymbolUtil.isApplicableByPsi(declaration)) return
        if (refHolder.isUsedLocally(declaration)) return // even for non-private declarations our refHolder might have usage info
        val mustBeLocallyReferenced = declaration is KtParameter ||
                                      declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) ||
                                      ((declaration.parent as? KtClassBody)?.parent as? KtClassOrObject)?.isLocal == true

        val nameIdentifier = declaration.nameIdentifier
        if (mustBeLocallyReferenced &&
            !SuppressionUtil.inspectionResultSuppressed(declaration, deadCodeInspection) &&
            declaration.annotationEntries.isEmpty() //instead of slow implicit usages checks
        ) {
            val description = declaration.describe() ?: return
            val problemPsiElement = nameIdentifier ?: (declaration as? KtConstructor<*>)?.getConstructorKeyword() ?: declaration
            val info = UnusedSymbolUtil.createUnusedSymbolInfoBuilder(problemPsiElement, KotlinBaseHighlightingBundle.message("inspection.message.never.used", description), deadCodeInfoType, null)
              .registerFix(SafeDeleteFix(declaration), null, null, null, deadCodeKey)
              .create()
            holder.add(info)
        }
        else {
            val problemPsi = KotlinUnusedSymbolUtil.getPsiToReportProblem(declaration) { false } ?: return
            val message = declaration.describe()?.let { KotlinBaseHighlightingBundle.message("inspection.message.never.used", it) }
                          ?: return
            val fixes = KotlinUnusedSymbolUtil.createQuickFixes(declaration).toTypedArray()
            val builder = UnusedSymbolUtil.createUnusedSymbolInfoBuilder(problemPsi, message, deadCodeInfoType, null)

            fixes.forEach { builder.registerFix(it, null, null, null, deadCodeKey) }
            holder.add(builder.create())
        }
    }
}

class KotlinRefsHolder {
    val localRefs = mutableMapOf<KtDeclaration, KtElement>()

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
      KotlinBaseHighlightingBundle.message("safe.delete.text", declaration.name ?: declaration.text)

    override fun getText(): @IntentionName String {
       return name
    }

    override fun getFamilyName() = KotlinBaseHighlightingBundle.message("safe.delete.family")

    override fun startInWriteAction(): Boolean = false

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val element = startElement as? KtDeclaration ?: return
        if (element is KtParameter && element.parent is KtParameterList && element.parent?.parent is KtFunction) {
            // TODO: Implement K2 version of `RemoveUnusedFunctionParameterFix` and use it here.
            val parameterList = element.parent as KtParameterList
            WriteCommandAction.runWriteCommandAction(project, name, null, {
                parameterList.removeParameter(element)
            }, element.containingFile)
        } else {
            SafeDeleteHandler.invoke(project, arrayOf(element), false)
        }
    }
}