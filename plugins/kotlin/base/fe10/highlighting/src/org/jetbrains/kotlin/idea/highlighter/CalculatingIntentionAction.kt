// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.intention.AbstractEmptyIntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.fe10.highlighting.KotlinBaseFe10HighlightingBundle
import javax.swing.Icon

class CalculatingIntentionAction : AbstractEmptyIntentionAction(), LowPriorityAction, Iconable {
    override fun getText(): String = KotlinBaseFe10HighlightingBundle.message("intention.calculating.text")

    override fun getFamilyName(): String = KotlinBaseFe10HighlightingBundle.message("intention.calculating.text")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override fun equals(other: Any?): Boolean = this === other || other is CalculatingIntentionAction

    override fun hashCode(): Int = 42

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.Preview
}
