// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.liftToExpect
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Tests:
 *
 *  - [org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated.ConvertSealedClassToEnum]
 *  - [org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated.Other.testConvertActualSealedClassToEnum]
 *  - [org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated.Other.testConvertExpectSealedClassToEnum]
 *
 *  - [org.jetbrains.kotlin.idea.k2.intentions.tests.K2IntentionTestGenerated.ConvertSealedClassToEnum]
 *  - [org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated.Other.testConvertActualSealedClassToEnum]
 *  - [org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated.Other.testConvertExpectSealedClassToEnum]
 */
internal class ConvertSealedClassToEnumIntention : SelfTargetingRangeIntention<KtClass>(
    KtClass::class.java,
    KotlinBundle.lazyMessage("convert.to.enum.class")
) {
    override fun startInWriteAction(): Boolean = false

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun applicabilityRange(element: KtClass): TextRange? {
        val nameIdentifier = element.nameIdentifier ?: return null
        val sealedKeyword = element.modifierList?.getModifier(KtTokens.SEALED_KEYWORD) ?: return null

        allowAnalysisOnEdt {
            analyze(element) {
                val symbol = element.symbol as? KaClassSymbol ?: return null
                val superTypesNotAny = symbol.superTypes.mapNotNull { it.symbol as? KaClassSymbol }.filter { superClassSymbol ->
                    superClassSymbol.classId != StandardClassIds.Any
                }
                if (superTypesNotAny.isNotEmpty()) return null
                return TextRange(sealedKeyword.startOffset, nameIdentifier.endOffset)
            }
        }
    }

    override fun applyTo(element: KtClass, editor: Editor?) {
        val project = element.project
        val klass = liftToExpect(element) as? KtClass ?: element

        val searchAction = {
            HierarchySearchRequest(klass, klass.useScope, false).searchInheritors().mapNotNull { it.unwrapped }
        }
        val subclasses: List<PsiElement> = if (element.isPhysical) {
            project.runSynchronouslyWithProgress(KotlinBundle.message("searching.inheritors"), true, searchAction) ?: return
        } else {
            searchAction().map { subClass ->
                // search finds physical elements
                try {
                    PsiTreeUtil.findSameElementInCopy(subClass, klass.containingFile)
                } catch (_: IllegalStateException) {
                    return
                }
            }
        }

        val subclassesByContainer: Map<KtClass?, List<PsiElement>> = subclasses.sortedBy { it.textOffset }.groupBy {
            if (it !is KtObjectDeclaration) return@groupBy null
            if (it.superTypeListEntries.size != 1) return@groupBy null
            val containingClass = it.containingClassOrObject as? KtClass ?: return@groupBy null
            if (containingClass != klass && liftToExpect(containingClass) != klass) return@groupBy null
            containingClass
        }

        val inconvertibleSubclasses: List<PsiElement> = subclassesByContainer[null] ?: emptyList()
        if (inconvertibleSubclasses.isNotEmpty()) {
            return showError(
                KotlinBundle.message("all.inheritors.must.be.nested.objects.of.the.class.itself.and.may.not.inherit.from.other.classes.or.interfaces"),
                inconvertibleSubclasses,
                project,
                editor
            )
        }

        @Suppress("UNCHECKED_CAST")
        val nonSealedClasses = (subclassesByContainer.keys as Set<KtClass>).filter { !it.isSealed() }
        if (nonSealedClasses.isNotEmpty()) {
            return showError(
                KotlinBundle.message("all.expected.and.actual.classes.must.be.sealed.classes"),
                nonSealedClasses,
                project,
                editor
            )
        }

        runWriteAction {
            if (subclassesByContainer.isNotEmpty()) {
                subclassesByContainer.forEach { (currentClass, currentSubclasses) ->
                    processClass(currentClass!!, currentSubclasses, project)
                }
            } else {
                processClass(klass, emptyList(), project)
            }
        }
    }

    private fun showError(@Nls message: String, elements: List<PsiElement>, project: Project, editor: Editor?) {
        if (elements.firstOrNull()?.isPhysical == false) return
        val elementDescriptions = elements.map {
            ElementDescriptionUtil.getElementDescription(it, RefactoringDescriptionLocation.WITHOUT_PARENT)
        }

        @NlsSafe
        val errorText = buildString {
            append(message)
            append(KotlinBundle.message("following.problems.are.found"))
            elementDescriptions.sorted().joinTo(this)
        }

        return CommonRefactoringUtil.showErrorHint(project, editor, errorText, text, null)
    }

    private fun processClass(klass: KtClass, subclasses: List<PsiElement>, project: Project) {
        val needSemicolon = klass.declarations.size > subclasses.size
        val movedDeclarations = run {
            val subclassesSet = subclasses.toSet()
            klass.declarations.filter { it in subclassesSet }
        }

        val psiFactory = KtPsiFactory(project)

        val comma = psiFactory.createComma()
        val semicolon = psiFactory.createSemicolon()

        val constructorCallNeeded = klass.hasExplicitPrimaryConstructor() || klass.secondaryConstructors.isNotEmpty()

        val entriesToAdd = movedDeclarations.mapIndexed { i, subclass ->
            subclass as KtObjectDeclaration

            val entryText = buildString {
                append(subclass.name)
                if (constructorCallNeeded) {
                    append((subclass.superTypeListEntries.firstOrNull() as? KtSuperTypeCallEntry)?.valueArgumentList?.text ?: "()")
                }
            }

            val entry = psiFactory.createEnumEntry(entryText)
            subclass.body?.let { body -> entry.add(body) }

            if (i < movedDeclarations.lastIndex) {
                entry.add(comma)
            } else if (needSemicolon) {
                entry.add(semicolon)
            }

            entry
        }

        movedDeclarations.forEach { it.delete() }

        klass.removeModifier(KtTokens.SEALED_KEYWORD)
        if (klass.isInterface()) {
            klass.getClassOrInterfaceKeyword()?.replace(psiFactory.createClassKeyword())
        }
        klass.addModifier(KtTokens.ENUM_KEYWORD)

        if (entriesToAdd.isNotEmpty()) {
            val firstEntry = entriesToAdd
                .reversed()
                .asSequence()
                .map { klass.addDeclarationBefore(it, null) }
                .last()
            // TODO: Add formatter rule
            firstEntry.parent.addBefore(psiFactory.createNewLine(), firstEntry)
        } else if (needSemicolon) {
            klass.declarations.firstOrNull()?.let { anchor ->
                val delimiter = anchor.parent.addBefore(semicolon, anchor).reformatted()
                delimiter.reformatted()
            }
        }
    }
}