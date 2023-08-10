// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.SuppressionUtil
import com.intellij.codeInspection.ex.InspectionProfileWrapper
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.base.psi.mustHaveNonEmptyPrimaryConstructor
import org.jetbrains.kotlin.idea.inspections.describe
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.containingClass

class KotlinUnusedHighlightingVisitor(private val ktFile: KtFile, private val holder: KotlinRefsHolder) {

    internal fun collectHighlights(progress: ProgressIndicator, infos: MutableList<HighlightInfo>) {
        val profile = InspectionProjectProfileManager.getInstance(ktFile.project).getCurrentProfile().let { p ->
            InspectionProfileWrapper.getCustomInspectionProfileWrapper(ktFile)?.let { it.apply(p).inspectionProfile } ?: p
        }

        val deadCodeKey = HighlightDisplayKey.find("UnusedSymbol") ?: return
        val deadCodeInspection = profile.getUnwrappedTool("UnusedSymbol", ktFile) as? LocalInspectionTool ?: return

        val deadCodeInfoType =
            HighlightInfoType.HighlightInfoTypeImpl(
                profile.getErrorLevel(deadCodeKey, ktFile).getSeverity(),
                ((profile.getEditorAttributes(deadCodeKey.toString(), ktFile)) ?: HighlightInfoType.UNUSED_SYMBOL.getAttributesKey())
            )
        if (!profile.isToolEnabled(deadCodeKey, ktFile)) return
        if (!HighlightingLevelManager.getInstance(ktFile.project).shouldInspect(ktFile)) return

        for (declaration in CollectHighlightsUtil.getElementsInRange(ktFile, 0, ktFile.getTextLength()).filterIsInstance<KtNamedDeclaration>()) {
            if (declaration is KtClassOrObject && declaration.isTopLevel()) {
                //top level private classes are visible from java as package locals
                continue
            }

            if (declaration is KtObjectDeclaration && declaration.isCompanion()) {
                //can be used implicitly by referencing members
                continue
            }

            if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
                continue
            }

            if (declaration.hasModifier(KtTokens.OPERATOR_KEYWORD)) {
                //we don't highlight operators now, can be improved later
                continue
            }

            if (declaration is KtParameter) {
                val function = declaration.ownerFunction ?: continue
                if (function is KtPrimaryConstructor) {
                    val containingClass = function.containingClass() ?: continue
                    if (containingClass.mustHaveNonEmptyPrimaryConstructor()) {
                        //parameters are uses implicitly in equals/hashCode/etc
                        continue
                    }
                    if (declaration.hasValOrVar() && !declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                        //can be used outside
                        continue
                    }
                } else if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD) ||
                    function.hasModifier(KtTokens.OPEN_KEYWORD) ||
                    function.hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
                    function.containingClass()?.isInterface() == true) {
                    //function can be in the hierarchy
                    continue
                } else if (function is KtFunctionLiteral) {
                    continue
                }
            }

            val mustBeLocallyReferenced = declaration is KtParameter ||
                                          declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) ||
                                          ((declaration.parent as? KtClassBody)?.parent as? KtClassOrObject)?.isLocal == true

            val nameIdentifier = declaration.nameIdentifier
            if (mustBeLocallyReferenced &&
                !holder.isUsedLocally(declaration) &&
                !SuppressionUtil.inspectionResultSuppressed(declaration, deadCodeInspection) &&
                declaration.annotationEntries.isEmpty() //instead of slow implicit usages checks
            ) {
                val description = declaration.describe() ?: continue
                val info = HighlightInfo.newHighlightInfo(deadCodeInfoType)
                    .range(nameIdentifier ?: (declaration as? KtConstructor<*>)?.getConstructorKeyword() ?: declaration)
                    .descriptionAndTooltip(KotlinBaseHighlightingBundle.message("inspection.message.never.used", description))
                    .group(GeneralHighlightingPass.POST_UPDATE_ALL)
                    .registerFix(SafeDeleteFix(declaration), null, null, null, deadCodeKey)
                    .create()
                infos.addIfNotNull(info)
            }
        }
    }
}

class KotlinRefsHolder {
    val localRefs = mutableMapOf<KtDeclaration, KtElement>()

    fun registerLocalRef(declaration: PsiElement?, reference: KtElement) {
        if (declaration is KtDeclaration) {
            localRefs.put(declaration, reference)
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