// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.liftToExpect
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

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
internal class ConvertSealedClassToEnumIntention : KotlinApplicableModCommandAction<KtClass, Unit>(KtClass::class) {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.to.enum.class")

    override fun stopSearchAt(element: PsiElement, context: ActionContext): Boolean =
        element is KtBlockExpression

    override fun isApplicableByPsi(element: KtClass): Boolean =
        element.takeIf { it.nameIdentifier != null }
            ?.modifierList?.getModifier(KtTokens.SEALED_KEYWORD) != null

    override fun getApplicableRanges(element: KtClass): List<TextRange> {
        val nameIdentifier = element.nameIdentifier ?: return emptyList()
        val sealedKeyword = element.modifierList?.getModifier(KtTokens.SEALED_KEYWORD) ?: return emptyList()
        val range = TextRange(sealedKeyword.startOffset, nameIdentifier.endOffset).shiftLeft(element.startOffset)
        return listOf(range)
    }

    override fun KaSession.prepareContext(element: KtClass): Unit? {
        val notEmptySuperTypes = analyze(element) {
            val symbol = element.symbol as? KaClassSymbol ?: return null
            val superTypesNotAny =
                symbol.superTypes
                    .mapNotNull { it.symbol as? KaClassSymbol }
                    .filter { it.classId != StandardClassIds.Any }
            superTypesNotAny.isNotEmpty()
        }
        if (notEmptySuperTypes) return null

        val klass = liftToExpect(element) as? KtClass ?: element
        val subclassesByContainer =
            // fast check: look up subclasses of the sealed class within the same file only
            findSubclassesByContainer(klass, LocalSearchScope(element.containingFile))

        val inconvertibleSubclasses = subclassesByContainer[null] ?: emptyList()
        if (inconvertibleSubclasses.isNotEmpty()) {
            // All inheritors must be nested objects of the class itself and may not inherit from other classes or interfaces.
            return null
        }
        val nonSealedClasses = (subclassesByContainer.keys as? Set<*>)
            ?.mapNotNull { (it as? SmartPsiElementPointer<*>)?.element }
            ?.filter { !klass.isSealed() } ?: emptyList()
        if (nonSealedClasses.isNotEmpty()) {
            // All expected and actual classes must be sealed classes.
            return null
        }

        return Unit
    }

    override fun invoke(actionContext: ActionContext, element: KtClass, elementContext: Unit, updater: ModPsiUpdater) {
        val originalFile = updater.getOriginalFile(element.containingFile)
        // a real file ktClass instance is needed for the search
        val klass = originalFile.findElementAt(actionContext.offset)?.parent as? KtClass ?: return
        // prepareContext looks up for subclasses of the sealed class within the same file only
        val subclassesByContainer =
            findSubclassesByContainer(klass, klass.useScope)

        val inconvertibleSubclasses = subclassesByContainer[null] ?: emptyList()
        if (inconvertibleSubclasses.isNotEmpty()) {
            updater.cancel(
                errorText(
                    KotlinBundle.message("all.inheritors.must.be.nested.objects.of.the.class.itself.and.may.not.inherit.from.other.classes.or.interfaces"),
                    inconvertibleSubclasses.mapNotNull { it.element },
                )
            )
            return
        }

        val project = element.project

        val nonSealedClasses = (subclassesByContainer.keys as? Set<*>)
            ?.mapNotNull { (it as? SmartPsiElementPointer<*>)?.element }
            ?.filter { !klass.isSealed() } ?: emptyList()
        if (nonSealedClasses.isNotEmpty()) {
            updater.cancel(
                errorText(
                    KotlinBundle.message("all.expected.and.actual.classes.must.be.sealed.classes"),
                    nonSealedClasses,
                )
            )
            return
        }

        if (subclassesByContainer.isNotEmpty()) {
            for ((currentClass, currentSubclasses) in subclassesByContainer) {
                val writableClass = currentClass?.element?.let(updater::getWritable) ?: continue
                val writableSubclasses = currentSubclasses.mapNotNull {
                    it.element?.let(updater::getWritable)
                }
                processClass(writableClass, writableSubclasses, project)
            }
        } else {
            val writableClass = updater.getWritable(klass) ?: return
            processClass(writableClass, emptyList(), project)
        }
    }

    private fun findSubclassesByContainer(
        klass: KtClass,
        searchScope: SearchScope
    ): Map<SmartPsiElementPointer<KtClass>?, List<SmartPsiElementPointer<PsiElement>>> {
        val subclasses =
            HierarchySearchRequest(klass, searchScope, false)
                .searchInheritors().asIterable().mapNotNull { it.unwrapped }
        val subclassesByContainer =
            subclasses.sortedBy { it.textOffset }.map(PsiElement::createSmartPointer).groupBy {
                val ktObjectDeclaration = it.element as? KtObjectDeclaration ?: return@groupBy null
                if (ktObjectDeclaration.superTypeListEntries.size != 1) return@groupBy null
                val containingClass = ktObjectDeclaration.containingClassOrObject as? KtClass ?: return@groupBy null
                if (containingClass != klass && liftToExpect(containingClass) != klass) return@groupBy null
                containingClass.createSmartPointer()
            }
        return subclassesByContainer
    }

    @NlsSafe
    private fun errorText(@Nls message: String, elements: List<PsiElement>): String {
        val elementDescriptions = elements.map {
            ElementDescriptionUtil.getElementDescription(it, RefactoringDescriptionLocation.WITHOUT_PARENT)
        }

        return buildString {
            append(message)
            append(KotlinBundle.message("following.problems.are.found"))
            elementDescriptions.sorted().joinTo(this)
        }
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
