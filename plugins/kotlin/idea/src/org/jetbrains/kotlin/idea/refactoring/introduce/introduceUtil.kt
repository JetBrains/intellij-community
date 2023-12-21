// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.surroundWith.KotlinSurrounderUtils
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.refactoring.selectElement
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.findElements
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.SmartList

fun showErrorHint(project: Project, editor: Editor, @NlsContexts.DialogMessage message: String, @NlsContexts.DialogTitle title: String) {
    KotlinSurrounderUtils.showErrorHint(project, editor, message, title, null)
}

fun showErrorHintByKey(project: Project, editor: Editor, messageKey: String, @NlsContexts.DialogTitle title: String) {
    showErrorHint(project, editor, KotlinBundle.message(messageKey), title)
}

fun selectElementsWithTargetSibling(
    @NlsContexts.DialogTitle operationName: String,
    editor: Editor,
    file: KtFile,
    @NlsContexts.DialogTitle title: String,
    elementKinds: Collection<ElementKind>,
    elementValidator: (List<PsiElement>) -> String?,
    getContainers: (elements: List<PsiElement>, commonParent: PsiElement) -> List<PsiElement>,
    continuation: (elements: List<PsiElement>, targetSibling: PsiElement) -> Unit
) {
    fun onSelectionComplete(elements: List<PsiElement>, targetContainer: PsiElement) {
        val physicalElements = elements.map { it.substringContextOrThis }
        val parent = PsiTreeUtil.findCommonParent(physicalElements)
            ?: throw AssertionError("Should have at least one parent: ${physicalElements.joinToString("\n")}")

        if (parent == targetContainer) {
            continuation(elements, physicalElements.first())
            return
        }

        val outermostParent = parent.getOutermostParentContainedIn(targetContainer)
        if (outermostParent == null) {
            showErrorHintByKey(file.project, editor, "cannot.refactor.no.container", operationName)
            return
        }

        continuation(elements, outermostParent)
    }

    selectElementsWithTargetParent(operationName, editor, file, title, elementKinds, elementValidator, getContainers, ::onSelectionComplete)
}

fun selectElementsWithTargetParent(
    @NlsContexts.DialogTitle operationName: String,
    editor: Editor,
    file: KtFile,
    @NlsContexts.DialogTitle title: String,
    elementKinds: Collection<ElementKind>,
    elementValidator: (List<PsiElement>) -> @NlsContexts.DialogMessage String?,
    getContainers: (elements: List<PsiElement>, commonParent: PsiElement) -> List<PsiElement>,
    continuation: (elements: List<PsiElement>, targetParent: PsiElement) -> Unit
) {
    fun showErrorHintByKey(key: String) {
        showErrorHintByKey(file.project, editor, key, operationName)
    }

    fun selectTargetContainer(elements: List<PsiElement>) {
        elementValidator(elements)?.let {
            showErrorHint(file.project, editor, it, operationName)
            return
        }

        val physicalElements = elements.map { it.substringContextOrThis }
        val parent = PsiTreeUtil.findCommonParent(physicalElements)
            ?: throw AssertionError("Should have at least one parent: ${physicalElements.joinToString("\n")}")

        val containers = getContainers(physicalElements, parent)
        if (containers.isEmpty()) {
            showErrorHintByKey("cannot.refactor.no.container")
            return
        }

        chooseContainerElementIfNecessary(
            containers,
            editor,
            title,
            true
        ) {
            continuation(elements, it)
        }
    }

    fun selectMultipleElements() {
        val startOffset = editor.selectionModel.selectionStart
        val endOffset = editor.selectionModel.selectionEnd

        val elements = elementKinds.flatMap { findElements(file, startOffset, endOffset, it).toList() }
        if (elements.isEmpty()) {
            return when (elementKinds.singleOrNull()) {
                ElementKind.EXPRESSION -> showErrorHintByKey("cannot.refactor.no.expression")
                ElementKind.TYPE_ELEMENT -> showErrorHintByKey("cannot.refactor.no.type")
                else -> showErrorHint(
                    file.project,
                    editor,
                    KotlinBundle.message("text.refactoring.can.t.be.performed.on.the.selected.code.element"),
                    title
                )
            }
        }

        selectTargetContainer(elements)
    }

    fun selectSingleElement() {
        selectElement(editor, file, false, elementKinds) { expr ->
            if (expr != null) {
                selectTargetContainer(listOf(expr))
            } else {
                if (!editor.selectionModel.hasSelection()) {
                    if (elementKinds.singleOrNull() == ElementKind.EXPRESSION) {
                        val elementAtCaret = file.findElementAt(editor.caretModel.offset)
                        elementAtCaret?.getParentOfTypeAndBranch<KtProperty> { nameIdentifier }?.let {
                            return@selectElement selectTargetContainer(listOf(it))
                        }
                    }

                    editor.selectionModel.selectLineAtCaret()
                }
                selectMultipleElements()
            }
        }
    }

    editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    selectSingleElement()
}

fun findStringTemplateFragment(file: KtFile, startOffset: Int, endOffset: Int, kind: ElementKind): KtExpression? {
    if (kind != ElementKind.EXPRESSION) return null

    val startEntry = file.findElementAt(startOffset)?.getNonStrictParentOfType<KtStringTemplateEntry>() ?: return null
    val endEntry = file.findElementAt(endOffset - 1)?.getNonStrictParentOfType<KtStringTemplateEntry>() ?: return null

    if (startEntry.parent !is KtStringTemplateExpression || startEntry.parent != endEntry.parent) return null

    val prefixOffset = startOffset - startEntry.startOffset
    if (startEntry !is KtLiteralStringTemplateEntry && prefixOffset > 0) return null

    val suffixOffset = endOffset - endEntry.startOffset
    if (endEntry !is KtLiteralStringTemplateEntry && suffixOffset < endEntry.textLength) return null

    val prefix = startEntry.text.substring(0, prefixOffset)
    val suffix = endEntry.text.substring(suffixOffset)

    return K1ExtractableSubstringInfo(startEntry, endEntry, prefix, suffix).createExpression()
}

fun KotlinPsiRange.getPhysicalTextRange(): TextRange {
    return (elements.singleOrNull() as? KtExpression)?.extractableSubstringInfo?.contentRange ?: textRange
}

fun isObjectOrNonInnerClass(e: PsiElement): Boolean = e is KtObjectDeclaration || (e is KtClass && !e.isInner())

fun <T : KtDeclaration> insertDeclaration(declaration: T, targetSibling: PsiElement): T {
    val targetParent = targetSibling.parent

    val anchorCandidates = SmartList<PsiElement>()
    anchorCandidates.add(targetSibling)
    if (targetSibling is KtEnumEntry) {
        anchorCandidates.add(targetSibling.siblings().last { it is KtEnumEntry })
    }

    val anchor = anchorCandidates.minByOrNull { it.startOffset }!!.parentsWithSelf.first { it.parent == targetParent }
    val targetContainer = anchor.parent!!

    @Suppress("UNCHECKED_CAST")
    return (targetContainer.addBefore(declaration, anchor) as T).apply {
        targetContainer.addBefore(KtPsiFactory(declaration.project).createWhiteSpace("\n\n"), anchor)
    }
}

internal fun validateExpressionElements(elements: List<PsiElement>): String? {
    if (elements.any { it is KtConstructor<*> || it is KtParameter || it is KtTypeAlias || it is KtPropertyAccessor }) {
        return KotlinBundle.message("text.refactoring.is.not.applicable.to.this.code.fragment")
    }
    return null
}
