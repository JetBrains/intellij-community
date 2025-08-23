// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.CREATE_BY_PATTERN_MAY_NOT_REFORMAT
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.containsInside
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import java.util.function.Supplier

@Suppress("EqualsOrHashCode")
abstract class SelfTargetingIntention<TElement : PsiElement>(
    val elementType: Class<TElement>,
    @FileModifier.SafeFieldForPreview // should not depend on the file and affect fix behavior
    private var textGetter: Supplier<@IntentionName String>,
    @FileModifier.SafeFieldForPreview // should not depend on the file and affect fix behavior
    private var familyNameGetter: Supplier<@IntentionFamilyName String> = textGetter,
) : IntentionAction {

    protected val defaultText: @IntentionName String get() = defaultTextGetter.get()
    @FileModifier.SafeFieldForPreview // should not depend on the file and affect fix behavior
    protected val defaultTextGetter: Supplier<@IntentionName String> = textGetter

    @Deprecated("Replace with primary constructor")
    @Suppress("HardCodedStringLiteral")
    constructor(
        elementType: Class<TElement>,
        textGetter: () -> @IntentionName String,
        familyNameGetter: () -> @IntentionFamilyName String = textGetter,
        ) : this(elementType, Supplier { textGetter() }, Supplier { familyNameGetter() }) {
    }

    @Deprecated("Replace with the overloaded method")
    @Suppress("HardCodedStringLiteral")
    protected fun setTextGetter(textGetter: () -> @IntentionName String) {
        this.textGetter = Supplier { textGetter() }
    }

    protected fun setTextGetter(textGetter: Supplier<@IntentionName String>) {
        this.textGetter = textGetter
    }

    @Suppress("HardCodedStringLiteral")
    final override fun getText(): @IntentionName String = textGetter.get()

    // Not final because `KotlinApplicableIntentionBase` redefines `getFamilyName` as an abstract function and disregards
    // `familyNameGetter`.
    @Suppress("HardCodedStringLiteral")
    override fun getFamilyName(): @IntentionFamilyName String = familyNameGetter.get()

    protected fun setFamilyNameGetter(familyNameGetter: Supplier<@IntentionFamilyName String>) {
        this.familyNameGetter = familyNameGetter
    }

    abstract fun isApplicableTo(element: TElement, caretOffset: Int): Boolean

    abstract fun applyTo(element: TElement, editor: Editor?)

    open fun applyTo(element: TElement, project: Project, editor: Editor?) {
        applyTo(element, editor)
    }

    protected open val isKotlinOnlyIntention: Boolean = true

    /**
     * Override if the action should be available on library sources.
     * It means that it won't modify the code of the current file e.g., it implements the interface in project code or change some settings
     */
    protected open fun checkFile(file: PsiFile): Boolean {
        return BaseIntentionAction.canModify(file)
    }
    
    fun getTarget(offset: Int, file: PsiFile): TElement? {
        if (!checkFile(file)) return null
       
        val leaf1 = file.findElementAt(offset)
        val leaf2 = file.findElementAt(offset - 1)
        val commonParent = if (leaf1 != null && leaf2 != null) PsiTreeUtil.findCommonParent(leaf1, leaf2) else null

        var elementsToCheck: Sequence<PsiElement> = emptySequence()
        if (leaf1 != null) elementsToCheck += leaf1.parentsWithSelf.takeWhile { it != commonParent }
        if (leaf2 != null) elementsToCheck += leaf2.parentsWithSelf.takeWhile { it != commonParent }
        if (commonParent != null && commonParent !is PsiFile) elementsToCheck += commonParent.parentsWithSelf

        for (element in elementsToCheck) {
            @Suppress("UNCHECKED_CAST")
            if (elementType.isInstance(element)) {
                ProgressManager.checkCanceled()
                if (isApplicableTo(element as TElement, offset)) {
                    return element
                }
                if (visitTargetTypeOnlyOnce()) {
                    return null
                }
            }
            if (element.textRange.containsInside(offset) && skipProcessingFurtherElementsAfter(element)) break
        }
        return null
    }

    fun getTarget(editor: Editor, file: PsiFile): TElement? {
        if (isKotlinOnlyIntention && file !is KtFile) return null

        val offset = editor.caretModel.offset
        return getTarget(offset, file)
    }

    /** Whether to skip looking for targets after having processed the given element, which contains the cursor. */
    protected open fun skipProcessingFurtherElementsAfter(element: PsiElement): Boolean = element is KtBlockExpression

    protected open fun visitTargetTypeOnlyOnce(): Boolean = false

    final override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (isUnitTestMode()) {
            CREATE_BY_PATTERN_MAY_NOT_REFORMAT = true
        }
        try {
            return getTarget(editor, file) != null
        } finally {
            if (isUnitTestMode()) { // do not trigger additional class loading outside of tests
                CREATE_BY_PATTERN_MAY_NOT_REFORMAT = false
            }
        }
    }

    @FileModifier.SafeFieldForPreview // inspection should not depend on the file where the fix is applied
    var inspection: IntentionBasedInspection<TElement>? = null
        internal set

    final override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        editor ?: return
        val target = getTarget(editor, file) ?: return
        if (!preparePsiElementForWriteIfNeeded(target)) return
        applyTo(target, project, editor)
    }

    /**
     * If [startInWriteAction] returns true, that means that the platform already called `preparePsiElementForWrite`
     * for us (we do not want to call it again because it will throw if the intention is used with Intention Preview).
     *
     * Otherwise, we have to call it ourselves (see javadoc for [getElementToMakeWritable]).
     */
    protected open fun preparePsiElementForWriteIfNeeded(target: TElement): Boolean {
        if (startInWriteAction()) return true
        return FileModificationService.getInstance().preparePsiElementForWrite(target)
    }

    override fun startInWriteAction() = true

    override fun toString(): String = text

    override fun equals(other: Any?): Boolean {
        // Nasty code because IntentionWrapper itself does not override equals
        if (other is IntentionWrapper) return this == other.action
        if (other is IntentionBasedInspection<*>.IntentionBasedQuickFix) return this == other.intention
        return other is SelfTargetingIntention<*> && javaClass == other.javaClass && text == other.text
    }

    // Intentionally missed hashCode (IntentionWrapper does not override it)
}

