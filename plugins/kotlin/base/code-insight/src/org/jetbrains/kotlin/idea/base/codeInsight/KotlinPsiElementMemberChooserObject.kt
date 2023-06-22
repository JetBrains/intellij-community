// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.codeInsight.generation.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import javax.swing.Icon

class KotlinPsiElementMemberChooserObject(
    psiElement: KtElement,
    @NlsContexts.Label text: String,
    icon: Icon?
) : PsiElementMemberChooserObject(psiElement, text, icon), ClassMemberWithElement {
    override fun getParentNodeDelegate(): MemberChooserObject {
        val containingDeclaration = element.getParentOfTypes<KtElement>(strict = true, KtNamedDeclaration::class.java, KtFile::class.java)!!

        return ReadAction.nonBlocking<PsiElementMemberChooserObject> { getMemberChooserObject(containingDeclaration) }
            .expireWith(KotlinPluginDisposable.getInstance(containingDeclaration.project))
            .submit(AppExecutorUtil.getAppExecutorService())
            .get()
    }

    override fun getElement(): KtElement = psiElement as KtElement

    companion object {
        private val renderer = KtDeclarationRendererForSource.WITH_SHORT_NAMES.with {
            modifiersRenderer = modifiersRenderer.with {
                keywordsRenderer = keywordsRenderer.with { keywordFilter = KtRendererKeywordFilter.NONE }
            }
        }

        @JvmStatic
        fun getKotlinMemberChooserObject(declaration: KtDeclaration): KotlinPsiElementMemberChooserObject {
            return analyze(declaration) {
                val symbol = declaration.getSymbol()
                val text = getChooserText(symbol)
                val icon = getChooserIcon(declaration, symbol)
                KotlinPsiElementMemberChooserObject(declaration, text, icon)
            }
        }

        @JvmStatic
        fun getMemberChooserObject(declaration: PsiElement): PsiElementMemberChooserObject {
            return when (declaration) {
                is KtFile -> PsiElementMemberChooserObject(declaration, declaration.name)
                is KtDeclaration -> getKotlinMemberChooserObject(declaration)
                is PsiField -> PsiFieldMember(declaration)
                is PsiMethod -> PsiMethodMember(declaration)
                is PsiClass -> {
                    val text = PsiFormatUtil.formatClass(declaration, PsiFormatUtilBase.SHOW_NAME or PsiFormatUtilBase.SHOW_FQ_NAME)
                    PsiDocCommentOwnerMemberChooserObject(declaration, text, declaration.getIcon(0))
                }
                else -> {
                    val name = (declaration as? PsiNamedElement)?.name ?: "<No name>"
                    PsiElementMemberChooserObject(declaration, name)
                }
            }
        }

        private fun KtAnalysisSession.getChooserText(symbol: KtSymbol): @NlsSafe String {
            if (symbol is KtClassOrObjectSymbol) {
                val classId = symbol.classIdIfNonLocal
                if (classId != null) {
                    return classId.asFqNameString()
                }
            }

            if (symbol is KtDeclarationSymbol) {
                return symbol.render(renderer)
            }

            return ""
        }

        private fun KtAnalysisSession.getChooserIcon(element: PsiElement, symbol: KtSymbol): Icon? {
            val isClass = element is KtClass || element is PsiClass
            val flags = if (isClass) 0 else Iconable.ICON_FLAG_VISIBILITY

            return when (element) {
                is KtDeclaration -> getIconFor(symbol)
                else -> element.getIcon(flags)
            }
        }
    }
}