// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.allOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.symbols.containingSymbol
import org.jetbrains.kotlin.analysis.api.symbols.isSubClassOf
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Highlights the relation between supertypes and overriding members of a class or object.
 *
 * This is a Kotlin analog of the Java behavior where placing the caret on the `extends`/`implements` keyword
 * highlights the methods overriding/implementing members of the corresponding supertypes.
 * Since Kotlin has no such keywords, the handler is activated when the caret is on a supertype reference
 * in the supertype list of a class or object.
 *
 * It also works in the opposite direction: placing the caret on the `override` keyword of a member or its name
 * highlights the supertypes the overridden member comes from.
 *
 */
class KotlinHighlightOverridingMembersHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        createSuperTypeEntryHandler(editor, file, target)?.let { return it }
        return createOverrideKeywordOrNameHandler(editor, file, target)
    }

    private fun createSuperTypeEntryHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        val referenceExpression = target.parent as? KtNameReferenceExpression ?: return null
        val userType = referenceExpression.parent as? KtUserType ?: return null
        val typeReference = userType.parent as? KtTypeReference ?: return null
        val superTypeEntry = when (val parent = typeReference.parent) {
            is KtSuperTypeListEntry -> parent
            is KtConstructorCalleeExpression -> parent.parent as? KtSuperTypeCallEntry
            else -> null
        } ?: return null
        val classOrObject = (superTypeEntry.parent as? KtSuperTypeList)?.parent as? KtClassOrObject ?: return null
        return KotlinHighlightOverridingMembersHandler(editor, file, classOrObject, superTypeEntry)
    }

    private fun createOverrideKeywordOrNameHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        val declaration = getOverridingDeclaration(target) ?: return null
        val classOrObject = when (declaration) {
            is KtParameter ->
                if (declaration.hasValOrVar()) (declaration.ownerFunction as? KtPrimaryConstructor)?.containingClassOrObject else null
            else -> declaration.containingClassOrObject
        } ?: return null
        return KotlinHighlightOverriddenSuperTypesHandler(editor, file, classOrObject, declaration)
    }

    private fun getOverridingDeclaration(target: PsiElement): KtCallableDeclaration? {
        // Caret on the `override` keyword
        if (target.node?.elementType == KtTokens.OVERRIDE_KEYWORD) {
            return (target.parent as? KtModifierList)?.parent as? KtCallableDeclaration
        }

        // Caret is on the member's identifier name (function or property name)
        val declaration = target.parent as? KtCallableDeclaration ?: return null
        if (declaration.nameIdentifier == target && declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            return declaration
        }

        return null
    }
}

private class KotlinHighlightOverridingMembersHandler(
    editor: Editor,
    file: PsiFile,
    private val classOrObject: KtClassOrObject,
    private val superTypeEntry: KtSuperTypeListEntry,
) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {

    override fun getTargets(): List<PsiElement> = listOf(superTypeEntry)

    override fun highlightReferences(): Boolean = true

    override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
        selectionConsumer.consume(targets)
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun computeUsages(targets: List<PsiElement>) {
        val overridingMembers = allowAnalysisOnEdt {
            analyze(classOrObject) {
                findOverridingMembers()
            }
        }
        if (overridingMembers.isEmpty()) return

        superTypeEntry.typeReference?.let(::addOccurrence)
        for (it in overridingMembers.indices) {
            addOccurrence(overridingMembers[it])
        }
    }

    context(_: KaSession)
    private fun findOverridingMembers(): List<PsiElement> {
        val superClassSymbol = superTypeEntry.typeReference?.type?.expandedSymbol ?: return emptyList()

        val members = classOrObject.primaryConstructor?.valueParameters.orEmpty().filter { it.hasValOrVar() } +
                classOrObject.declarations.filterIsInstance<KtCallableDeclaration>()

        return members.mapNotNull { member ->
            if (!member.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return@mapNotNull null
            val memberSymbol = member.symbol as? KaCallableSymbol ?: return@mapNotNull null
            val overridesSuperTypeMember = memberSymbol.allOverriddenSymbols.any { overriddenSymbol ->
                val containingClass = overriddenSymbol.containingSymbol as? KaClassSymbol ?: return@any false
                superClassSymbol.isSameOrSubClassOf(containingClass)
            }
            if (overridesSuperTypeMember) member.nameIdentifier else null
        }
    }
}

private class KotlinHighlightOverriddenSuperTypesHandler(
    editor: Editor,
    file: PsiFile,
    private val classOrObject: KtClassOrObject,
    private val declaration: KtCallableDeclaration,
) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {

    override fun getTargets(): List<PsiElement> = listOf(declaration)

    override fun highlightReferences(): Boolean = true

    override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
        selectionConsumer.consume(targets)
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun computeUsages(targets: List<PsiElement>) {
        val superTypeReferences = allowAnalysisOnEdt {
            analyze(classOrObject) {
                findOverriddenSuperTypeReferences()
            }
        }
        if (superTypeReferences.isEmpty()) return

        declaration.nameIdentifier?.let(::addOccurrence)
        for (it in superTypeReferences.indices) {
            addOccurrence(superTypeReferences[it])
        }
    }

    context(_: KaSession)
    private fun findOverriddenSuperTypeReferences(): List<PsiElement> {
        val memberSymbol = declaration.symbol as? KaCallableSymbol ?: return emptyList()
        val superClassSymbols = memberSymbol.allOverriddenSymbols
            .mapNotNull { it.containingSymbol as? KaClassSymbol }
            .toList()
        if (superClassSymbols.isEmpty()) return emptyList()

        return classOrObject.superTypeListEntries.mapNotNull { entry ->
            val typeReference = entry.typeReference ?: return@mapNotNull null
            val entrySymbol = typeReference.type.expandedSymbol ?: return@mapNotNull null
            if (superClassSymbols.any { entrySymbol.isSameOrSubClassOf(it) }) typeReference else null
        }
    }
}

context(_: KaSession)
private fun KaClassSymbol.isSameOrSubClassOf(other: KaClassSymbol): Boolean = this == other || isSubClassOf(other)
