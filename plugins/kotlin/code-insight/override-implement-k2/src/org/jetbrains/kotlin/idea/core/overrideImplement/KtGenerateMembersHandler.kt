// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.overrideImplement

import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.allOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.components.fakeOverrideOriginal
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.base.KaKeywordsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaParameterDefaultValueRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaPropertyAccessorsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.KaExpandedTypeRenderingMode
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.invokeShortening
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.insertMembersAfter
import org.jetbrains.kotlin.idea.core.moveCaretIntoGeneratedElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.prevSiblingOfSameType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

@ApiStatus.Internal
abstract class KtGenerateMembersHandler(
    final override val toImplement: Boolean
) : AbstractGenerateMembersHandler<KtClassMember>() {

    override fun isClassNode(key: MemberChooserObject): Boolean = key is KaClassOrObjectSymbolChooserObject

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    override fun generateMembers(
        editor: Editor,
        classOrObject: KtClassOrObject,
        selectedElements: Collection<KtClassMember>,
        copyDoc: Boolean
    ) {
        val entryMembers = ActionUtil.underModalProgress(classOrObject.project, KotlinBundle.message("fix.change.signature.prepare")) {
                analyze(classOrObject) {
                    createMemberEntries(editor, classOrObject, selectedElements, copyDoc)
                }
            }

        val insertedBlocks = insertMembersAccordingToPreferredOrder(entryMembers, editor, classOrObject)

        // Reference shortening is done in a separate analysis session because the session need to be aware of the newly generated
        // members.
        fun collectShortenings(): List<ShortenCommand> = analyze(classOrObject) {
            insertedBlocks.mapNotNull { block ->
                val declarations = block.declarations.mapNotNull { it.element }
                val first = declarations.firstOrNull() ?: return@mapNotNull null
                val last = declarations.last()
                collectPossibleReferenceShortenings(first.containingKtFile, TextRange(first.startOffset, last.endOffset))
            }
        }

        val commands = ActionUtil.underModalProgress(classOrObject.project, KotlinBundle.message("fix.change.signature.prepare")) {
                collectShortenings()
            }

        runWriteAction {
            commands.forEach { it.invokeShortening() }
            val project = classOrObject.project
            val codeStyleManager = CodeStyleManager.getInstance(project)
            insertedBlocks.forEach { block ->
                block.declarations.forEach { declaration ->
                    declaration.element?.let { element ->
                        codeStyleManager.reformat(
                            element
                        )
                    }
                }
            }
            insertedBlocks.firstOrNull()?.declarations?.firstNotNullOfOrNull { it.element }?.let {
                moveCaretIntoGeneratedElement(editor, it)
            }
        }
    }

    context(session: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createMemberEntries(
        editor: Editor,
        currentClass: KtClassOrObject,
        selectedElements: Collection<KtClassMember>,
        copyDoc: Boolean
    ): List<MemberEntry> {
        val selectedMemberSymbolsAndGeneratedPsi = selectedElements.mapNotNull { member ->
            with (session) { member.memberInfo.symbolPointer.restoreSymbol() }?.let { it to member }
        }.associate { (symbol, member) ->
            symbol to generateMember(currentClass.project, member, symbol, currentClass, copyDoc)
        }

        if (selectedMemberSymbolsAndGeneratedPsi.isEmpty()) return emptyList()

        val classBody = currentClass.body
        val offset = editor.caretModel.offset

        // Insert members at the cursor position if the cursor is within the class body. Or, if there is no body, generate the body and put
        // stuff in it.
        if (classBody == null || isCursorInsideClassBodyExcludingBraces(classBody, offset)) {
            return selectedMemberSymbolsAndGeneratedPsi.values.map { MemberEntry.NewEntry(it) }
        }

        // Insert members at positions such that the result aligns with ordering of members in super types.
        return getMembersOrderedByRelativePositionsInSuperTypes(currentClass, selectedMemberSymbolsAndGeneratedPsi)
    }

    private fun isCursorInsideClassBodyExcludingBraces(classBody: KtClassBody, offset: Int): Boolean {
        return classBody.textRange.contains(offset)
                && classBody.lBrace?.textRange?.contains(offset) == false
                && classBody.rBrace?.textRange?.contains(offset) == false
    }

    /**
     * Given a class and some stub implementation of overridden members, output all the callable members in the desired order. For example,
     * consider the following code
     *
     * ```
     * interface Super {
     *   fun a()
     *   fun b()
     *   fun c()
     * }
     *
     * class Sub: Super {
     *   override fun b() {}
     * }
     * ```
     *
     * Now this method is invoked with `Sub` as [currentClass] and `Super.a` and `Super.c` as [newMemberSymbolsAndGeneratedPsi]. This
     * method outputs `[NewEntry(Sub.a), ExistingEntry(Sub.b), NewEntry(Sub.c)]`.
     *
     * How does this work?
     *
     * Initially we put all existing members in [currentClass] into a doubly linked list in the order they appear in the source code. Then,
     * for each new member, the algorithm finds a target node nearby which this new member should be inserted. If the algorithm fails to
     * find a desired target node, then the new member is inserted at the end.
     *
     * With the above code as an example, initially the doubly linked list contains `[ExistingEntry(Sub.b)]`. Then for `a`, the algorithm
     * somehow (explained below) finds `ExistingEntry(Sub.b)` as the target node before which the new member `a` should be inserted. So now
     * the doubly linked list contains `[NewEntry(Sub.a), ExistingEntry(Sub.b)]`. Similar steps are done for `c`.
     *
     * How does the algorithm find the target node and how does it decide whether to insert the new member before or after the target node?
     *
     * Throughout the algorithm, we maintain a map that tracks super member declarations for each member in the doubly linked list. For
     * example, initially, the map contains `{ Super.b -> ExistingEntry(Sub.b) }`
     *
     * Given a new member, the algorithm first finds the PSI that declares this member in the super class. Then it traverses all the
     * sibling members before this PSI element. With the above example, there is nothing before `Super.a`. Next it traverses all the
     * sibling members after this PSI element. With the above example, it finds `Super.b`, which exists in the map. So the algorithm now
     * knows `Sub.a` should be inserted before `Sub.b`.
     *
     * @param currentClass the class where the generated member code will be placed in
     * @param newMemberSymbolsAndGeneratedPsi the generated members to insert into the class. For each entry in the map, the key is a
     * callable symbol for an overridable member that the user has picked to override (or implement), and the value is the stub
     * implementation for the chosen symbol.
     */
    context(_: KaSession)
private fun getMembersOrderedByRelativePositionsInSuperTypes(
        currentClass: KtClassOrObject,
        newMemberSymbolsAndGeneratedPsi: Map<KaCallableSymbol, KtCallableDeclaration>
    ): List<MemberEntry> {

        // This doubly linked list tracks the preferred ordering of members.
        val sentinelHeadNode = DoublyLinkedNode<MemberEntry>()
        val sentinelTailNode = DoublyLinkedNode<MemberEntry>()
        sentinelHeadNode.append(sentinelTailNode)

        // Traverse existing members in the current class and populate
        // - a doubly linked list tracking the order
        // - a map that tracks a member (as a doubly linked list node) in the current class and its overridden members in super classes (as
        // a PSI element). This map is to allow fast look up from a super PSI element to a member entry in the current class
        val existingDeclarations = currentClass.declarations.filterIsInstance<KtCallableDeclaration>()
        val superPsiToMemberEntry = mutableMapOf<PsiElement, DoublyLinkedNode<MemberEntry>>().apply {
            for (existingDeclaration in existingDeclarations) {
                val node: DoublyLinkedNode<MemberEntry> = DoublyLinkedNode(MemberEntry.ExistingEntry(existingDeclaration))
                sentinelTailNode.prepend(node)
                val callableSymbol = existingDeclaration.symbol as? KaCallableSymbol ?: continue
                for (overriddenSymbol in callableSymbol.allOverriddenSymbols) {
                    put(overriddenSymbol.psi ?: continue, node)
                }
            }
        }

        // Note on implementation: here we need the original ordering defined in the source code, so we stick to PSI rather than using
        // `KtMemberScope` because the latter does not guarantee members are traversed in the original order. For example the
        // FIR implementation groups overloaded functions together.
        outer@ for ((selectedSymbol, generatedPsi) in newMemberSymbolsAndGeneratedPsi) {
            val superSymbol = selectedSymbol.fakeOverrideOriginal
            val superPsi = superSymbol.psi
            if (superPsi == null) {
                // This normally should not happen, but we just try to play safe here.
                sentinelTailNode.prepend(DoublyLinkedNode(MemberEntry.NewEntry(generatedPsi)))
                continue
            }
            if (superPsiToMemberEntry.isNotEmpty()) {
                val currentStubElement = (superPsi as? StubBasedPsiElementBase<*>)?.stub
                if (currentStubElement != null) {
                    val parentStub = currentStubElement.parentStub
                    val childrenStubs = parentStub.childrenStubs
                    val currentIdx = childrenStubs.indexOf(currentStubElement)
                    var idx = currentIdx - 1
                    while (idx >= 0) {
                        val matchedNode = superPsiToMemberEntry[childrenStubs[idx].psi]
                        if (matchedNode != null) {
                            val newNode = DoublyLinkedNode<MemberEntry>(MemberEntry.NewEntry(generatedPsi))
                            matchedNode.append(newNode)
                            superPsiToMemberEntry[superPsi] = newNode
                            continue@outer
                        }
                        idx--
                    }
                    idx = currentIdx + 1
                    while (idx < childrenStubs.size) {
                        val matchedNode = superPsiToMemberEntry[childrenStubs[idx].psi]
                        if (matchedNode != null) {
                            val newNode = DoublyLinkedNode<MemberEntry>(MemberEntry.NewEntry(generatedPsi))
                            matchedNode.prepend(newNode)
                            superPsiToMemberEntry[superPsi] = newNode
                            continue@outer
                        }
                        idx++
                    }
                } else {
                    var currentPsi = superPsi.prevSibling
                    while (currentPsi != null) {
                        val matchedNode = superPsiToMemberEntry[currentPsi]
                        if (matchedNode != null) {
                            val newNode = DoublyLinkedNode<MemberEntry>(MemberEntry.NewEntry(generatedPsi))
                            matchedNode.append(newNode)
                            superPsiToMemberEntry[superPsi] = newNode
                            continue@outer
                        }
                        currentPsi = currentPsi.prevSibling
                    }
                    currentPsi = superPsi.nextSibling
                    while (currentPsi != null) {
                        val matchedNode = superPsiToMemberEntry[currentPsi]
                        if (matchedNode != null) {
                            val newNode = DoublyLinkedNode<MemberEntry>(MemberEntry.NewEntry(generatedPsi))
                            matchedNode.prepend(newNode)
                            superPsiToMemberEntry[superPsi] = newNode
                            continue@outer
                        }
                        currentPsi = currentPsi.nextSibling
                    }
                }
            }
            val newNode = DoublyLinkedNode<MemberEntry>(MemberEntry.NewEntry(generatedPsi))
            superPsiToMemberEntry[superPsi] = newNode
            sentinelTailNode.prepend(newNode)
        }
        return sentinelHeadNode.toListSkippingNulls()
    }

    private fun insertMembersAccordingToPreferredOrder(
        symbolsInPreferredOrder: List<MemberEntry>,
        editor: Editor,
        currentClass: KtClassOrObject
    ): List<InsertedBlock> {
        if (symbolsInPreferredOrder.isEmpty()) return emptyList()
        var firstAnchor: PsiElement? = null
        if (symbolsInPreferredOrder.first() is MemberEntry.NewEntry) {
            val firstExistingEntry = symbolsInPreferredOrder.firstIsInstanceOrNull<MemberEntry.ExistingEntry>()
            if (firstExistingEntry != null) {
                firstAnchor = firstExistingEntry.psi.prevSiblingOfSameType() ?: currentClass.body?.lBrace
            }
        }

        val insertionBlocks = mutableListOf<InsertionBlock>()
        var currentAnchor = firstAnchor
        val currentBatch = mutableListOf<KtCallableDeclaration>()
        fun updateBatch() {
            if (currentBatch.isNotEmpty()) {
                insertionBlocks += InsertionBlock(currentBatch.toList(), currentAnchor)
                currentBatch.clear()
            }
        }
        for (entry in symbolsInPreferredOrder) {
            when (entry) {
                is MemberEntry.ExistingEntry -> {
                    updateBatch()
                    currentAnchor = entry.psi
                }

                is MemberEntry.NewEntry -> {
                    currentBatch += entry.psi
                }
            }
        }
        updateBatch()

        //do not reformat on WA finish automatically, first we need to shorten references
        return PostprocessReformattingAspect.getInstance(currentClass.project).postponeFormattingInside(Computable {
            runWriteAction {
                insertionBlocks.map { (newDeclarations, anchor) ->
                    InsertedBlock(insertMembersAfter(editor, currentClass, newDeclarations, anchor = anchor))
                }
            }
        })
    }

    private class DoublyLinkedNode<T>(val t: T? = null) {
        private var prev: DoublyLinkedNode<T>? = null
        private var next: DoublyLinkedNode<T>? = null

        fun append(node: DoublyLinkedNode<T>) {
            val next = this.next
            this.next = node
            node.prev = this
            node.next = next
            next?.prev = node
        }

        fun prepend(node: DoublyLinkedNode<T>) {
            val prev = this.prev
            this.prev = node
            node.next = this
            node.prev = prev
            prev?.next = node
        }

        fun toListSkippingNulls(): List<T> {
            var current: DoublyLinkedNode<T>? = this
            return buildList {
                while (current != null) {
                    current?.let {
                        if (it.t != null) add(it.t)
                        current = it.next
                    }
                }
            }
        }
    }

    private sealed class MemberEntry {
        data class ExistingEntry(val psi: KtCallableDeclaration) : MemberEntry()
        data class NewEntry(val psi: KtCallableDeclaration) : MemberEntry()
    }

    companion object {
        @KaExperimentalApi
        val renderer = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
            keywordsRenderer = KaKeywordsRenderer.NONE

            returnTypeFilter = KaCallableReturnTypeFilter.ALWAYS

            parameterDefaultValueRenderer = KaParameterDefaultValueRenderer.THREE_DOTS

            typeRenderer = typeRenderer.with {
                expandedTypeRenderingMode = KaExpandedTypeRenderingMode.RENDER_ABBREVIATED_TYPE_WITH_EXPANDED_TYPE_COMMENT
            }

            withoutLabel()

            annotationRenderer = annotationRenderer.with {
                annotationFilter = KaRendererAnnotationsFilter.NONE
            }
            modifiersRenderer = modifiersRenderer.with {
                keywordsRenderer = keywordsRenderer.with {
                    keywordFilter = KaRendererKeywordFilter.onlyWith(KtTokens.VARARG_KEYWORD)
                }
            }

            propertyAccessorsRenderer = KaPropertyAccessorsRenderer.NONE
        }
    }

    /** A block of code (represented as a list of Kotlin declarations) that should be inserted at a given anchor. */
    private data class InsertionBlock(val declarations: List<KtDeclaration>, val anchor: PsiElement?)

    /** A block of generated code. The code is represented as a list of Kotlin declarations that are defined one after another. */
    private data class InsertedBlock(val declarations: List<SmartPsiElementPointer<KtDeclaration>>)
}