// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.Divider
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
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
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.base.psi.mustHaveNonEmptyPrimaryConstructor
import org.jetbrains.kotlin.idea.inspections.describe
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass

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
            // highlight visible symbols first
            for (declaration in dividedElements.inside()) {
                if (declaration is KtNamedDeclaration) {
                    handleDeclaration(declaration, deadCodeInspection, deadCodeInfoType, deadCodeKey, holder)
                }
            }
            for (declaration in dividedElements.outside()) {
                if (declaration is KtNamedDeclaration) {
                    handleDeclaration(declaration, deadCodeInspection, deadCodeInfoType, deadCodeKey, holder)
                }
            }
            true
        }
    }

    private fun handleDeclaration(declaration: KtNamedDeclaration,
                                  deadCodeInspection: LocalInspectionTool,
                                  deadCodeInfoType: HighlightInfoType.HighlightInfoTypeImpl,
                                  deadCodeKey: HighlightDisplayKey,
                                  holder: HighlightInfoHolder) {
        if (!declarationAllowedToBeUnused(declaration)) return

        val mustBeLocallyReferenced = declaration is KtParameter ||
                                      declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) ||
                                      ((declaration.parent as? KtClassBody)?.parent as? KtClassOrObject)?.isLocal == true

        val nameIdentifier = declaration.nameIdentifier
        if (mustBeLocallyReferenced &&
            !refHolder.isUsedLocally(declaration) &&
            !SuppressionUtil.inspectionResultSuppressed(declaration, deadCodeInspection) &&
            declaration.annotationEntries.isEmpty() //instead of slow implicit usages checks
        ) {
            val description = declaration.describe() ?: return
            val info = HighlightInfo.newHighlightInfo(deadCodeInfoType)
              .range(nameIdentifier ?: (declaration as? KtConstructor<*>)?.getConstructorKeyword() ?: declaration)
              .descriptionAndTooltip(KotlinBaseHighlightingBundle.message("inspection.message.never.used", description))
              .group(GeneralHighlightingPass.POST_UPDATE_ALL)
              .registerFix(SafeDeleteFix(declaration), null, null, null, deadCodeKey)
              .create()
            holder.add(info)
        }
    }

    private fun declarationAllowedToBeUnused(declaration: KtNamedDeclaration): Boolean {
        if (declaration is KtClassOrObject && declaration.isTopLevel()) {
            //top level private classes are visible from java as package locals
            return false
        }

        if (declaration is KtObjectDeclaration && declaration.isCompanion()) {
            //can be used implicitly by referencing members
            return false
        }

        if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            return false
        }

        if (declaration.hasModifier(KtTokens.OPERATOR_KEYWORD)) {
            //TODO we don't highlight operators now, can be improved later
            return false
        }

        if (declaration is KtParameter) {
            val function = declaration.ownerFunction ?: return true
            if (function is KtPrimaryConstructor) {
                val containingClass = function.containingClass() ?: return true
                if (containingClass.mustHaveNonEmptyPrimaryConstructor()) {
                    //parameters are uses implicitly in equals/hashCode/etc
                    return false
                }
                if (declaration.hasValOrVar() && !declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                    //can be used outside
                    return false
                }
            } else if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD) ||
                function.hasModifier(KtTokens.OPEN_KEYWORD) ||
                function.hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
                function.containingClass()?.isInterface() == true
            ) {
                //function can be in the hierarchy
                return false
            } else if (function is KtFunctionLiteral) {
                return false
            }
        }
        return true
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