// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.liftToExpect
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
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
internal class ConvertSealedClassToEnumIntention : PsiBasedModCommandAction<KtClass>(KtClass::class.java) {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.to.enum.class")

    override fun stopSearchAt(element: PsiElement, context: ActionContext): Boolean =
        element is KtBlockExpression

    override fun isElementApplicable(element: KtClass, context: ActionContext): Boolean {
        val nameIdentifier = element.nameIdentifier ?: return false
        val sealedKeyword = element.modifierList?.getModifier(KtTokens.SEALED_KEYWORD) ?: return false
        val range = TextRange(sealedKeyword.startOffset, nameIdentifier.endOffset)
        if (!range.containsOffset(context.offset)) return false

        analyze(element) {
            val symbol = element.symbol as? KaClassSymbol ?: return false
            val superTypesNotAny = symbol.superTypes.mapNotNull { it.symbol as? KaClassSymbol }.filter { superClassSymbol ->
                superClassSymbol.classId != StandardClassIds.Any
            }
            return superTypesNotAny.isEmpty()
        }
    }

    override fun perform(context: ActionContext, element: KtClass): ModCommand {
        val klass = liftToExpect(element) as? KtClass ?: element
        val subclasses = HierarchySearchRequest(klass, klass.useScope, false).searchInheritors().mapNotNull { it.unwrapped }
        val subclassesByContainer: Map<KtClass?, List<PsiElement>> = subclasses.sortedBy { it.textOffset }.groupBy {
            if (it !is KtObjectDeclaration) return@groupBy null
            if (it.superTypeListEntries.size != 1) return@groupBy null
            val containingClass = it.containingClassOrObject as? KtClass ?: return@groupBy null
            if (containingClass != klass && liftToExpect(containingClass) != klass) return@groupBy null
            containingClass
        }

        val inconvertibleSubclasses: List<PsiElement> = subclassesByContainer[null] ?: emptyList()
        if (inconvertibleSubclasses.isNotEmpty()) {
            return errorCommand(
                KotlinBundle.message("all.inheritors.must.be.nested.objects.of.the.class.itself.and.may.not.inherit.from.other.classes.or.interfaces"),
                inconvertibleSubclasses,
            )
        }

        @Suppress("UNCHECKED_CAST")
        val nonSealedClasses = (subclassesByContainer.keys as Set<KtClass>).filter { !it.isSealed() }
        if (nonSealedClasses.isNotEmpty()) {
            return errorCommand(
                KotlinBundle.message("all.expected.and.actual.classes.must.be.sealed.classes"),
                nonSealedClasses,
            )
        }

        return ModCommand.psiUpdate(element) { e, updater ->
            if (subclassesByContainer.isNotEmpty()) {
                for ((currentClass, currentSubclasses) in subclassesByContainer) {
                    val writableClass = updater.getWritable(currentClass) ?: continue
                    val writableSubclasses = currentSubclasses.mapNotNull { updater.getWritable(it) }
                    processClass(writableClass, writableSubclasses, e.project)
                }
            } else {
                val writableClass = updater.getWritable(klass) ?: return@psiUpdate
                processClass(writableClass, emptyList(), e.project)
            }
        }
    }

    private fun errorCommand(@Nls message: String, elements: List<PsiElement>): ModCommand {
        val elementDescriptions = elements.map {
            ElementDescriptionUtil.getElementDescription(it, RefactoringDescriptionLocation.WITHOUT_PARENT)
        }

        @NlsSafe
        val errorText = buildString {
            append(message)
            append(KotlinBundle.message("following.problems.are.found"))
            elementDescriptions.sorted().joinTo(this)
        }

        return ModCommand.error(errorText)
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
