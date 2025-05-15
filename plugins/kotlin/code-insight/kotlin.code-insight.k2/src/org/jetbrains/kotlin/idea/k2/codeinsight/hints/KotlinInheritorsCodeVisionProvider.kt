// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.daemon.impl.InheritorsLineMarkerNavigator
import com.intellij.codeInsight.hints.codeVision.InheritorsCodeVisionProvider
import com.intellij.java.analysis.OuterModelsModificationTrackerManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isInheritable
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.idea.searching.inheritors.findAllInheritors
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.idea.statistics.KotlinCodeVisionUsagesCollector
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.hasBody
import java.awt.event.MouseEvent

private const val COUNTING_LIMIT = 5

class KotlinInheritorsCodeVisionProvider : InheritorsCodeVisionProvider() {
    companion object {
        const val ID: String = "kotlin.inheritors"
    }

    override fun acceptsFile(file: PsiFile): Boolean = file.language == KotlinLanguage.INSTANCE

    override fun acceptsElement(element: PsiElement): Boolean = element is KtCallableDeclaration || element is KtClass

    override fun getVisionInfo(element: PsiElement, file: PsiFile): CodeVisionInfo? {
        val count = if (element is KtCallableDeclaration) {
            getCallableInheritors(element)
        } else if (element is KtClass) {
            getClassInheritors(element)
        } else {
            return null
        }

        if (count > 0) {
            val isAbstractMethod = when (element) {
                is KtCallableDeclaration -> !element.hasBody()
                is KtClass -> element.isInterface() || element.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                else -> return null
            }
            val toShow = minOf(count, COUNTING_LIMIT)
            val countIsExact = toShow == count
            val key = when {
              isAbstractMethod -> if (countIsExact) "hints.codevision.implementations.format" else "hints.codevision.implementations.too_many.format"
              element is KtClass -> if (countIsExact) "hints.codevision.inheritors.format" else "hints.codevision.inheritors.to_many.format"
              else -> if (countIsExact) "hints.codevision.overrides.format" else "hints.codevision.overrides.to_many.format"
            }
            return CodeVisionInfo(
                KotlinBundle.message(key, toShow), toShow, countIsExact
            )
        }

        return null
    }

    private fun getClassInheritors(element: KtClass): Int {
        return CachedValuesManager.getCachedValue(element, CachedValueProvider {
            val overrides = if (element.isInheritable()) element.findAllInheritors().take(COUNTING_LIMIT + 1).count() else 0
            CachedValueProvider.Result(overrides, OuterModelsModificationTrackerManager.getTracker(element.project))
        })
    }

    private fun getCallableInheritors(element: KtCallableDeclaration): Int {
        return CachedValuesManager.getCachedValue(element, CachedValueProvider {
            val overrides = if (element.isOverridable()) element.findAllOverridings().take(COUNTING_LIMIT + 1).count() else 0
            CachedValueProvider.Result(overrides, OuterModelsModificationTrackerManager.getTracker(element.project))
        })
    }

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        return getVisionInfo(element, file)?.text
    }

    override fun logClickToFUS(element: PsiElement, hint: String) {
        val location = when (element) {
            is KtClassOrObject -> if (element is KtClass && element.isInterface()) KotlinCodeVisionUsagesCollector.INTERFACE_LOCATION else KotlinCodeVisionUsagesCollector.CLASS_LOCATION
            is KtFunction -> KotlinCodeVisionUsagesCollector.FUNCTION_LOCATION
            is KtProperty -> KotlinCodeVisionUsagesCollector.PROPERTY_LOCATION
            else -> return
        }
        KotlinCodeVisionUsagesCollector.logInheritorsClicked(element.project, location)
    }

    override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        val lineMarkerNavigator = object : InheritorsLineMarkerNavigator() {
            override fun getMessageForDumbMode(): @NlsContexts.PopupContent String =
                KotlinBundle.message("notification.navigation.to.overriding.classes")
        }
        lineMarkerNavigator.navigate(event, (element as? KtNamedDeclaration)?.nameIdentifier ?: element)
    }

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = emptyList()

    override val id: String
        get() = ID
}