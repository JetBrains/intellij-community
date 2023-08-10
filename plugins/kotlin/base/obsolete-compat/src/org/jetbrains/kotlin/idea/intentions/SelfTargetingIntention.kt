// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

@Deprecated("Please use org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention")
abstract class SelfTargetingIntention<TElement : PsiElement>(
    elementType: Class<TElement>,
    @FileModifier.SafeFieldForPreview // should not depend on the file and affect fix behavior
    private var textGetter: () -> @IntentionName String,
    @FileModifier.SafeFieldForPreview // should not depend on the file and affect fix behavior
    private var familyNameGetter: () -> @IntentionFamilyName String = textGetter,
) : org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention<TElement>(
    elementType, textGetter, familyNameGetter
) {
    @Deprecated("Replace with primary constructor", ReplaceWith("SelfTargetingIntention<TElement>(elementType, { text }, { familyName })"))
    constructor(
        elementType: Class<TElement>,
        text: @IntentionName String,
        familyName: @IntentionFamilyName String = text,
    ) : this(elementType, { text }, { familyName })


    @Deprecated("Replace with `setTextGetter`", ReplaceWith("setTextGetter { text }"))
    protected fun setText(@IntentionName text: String) {
        this.textGetter = { text }
    }

    abstract override fun isApplicableTo(element: TElement, caretOffset: Int): Boolean

    abstract override fun applyTo(element: TElement, editor: Editor?)

    override fun startInWriteAction() = true
}

