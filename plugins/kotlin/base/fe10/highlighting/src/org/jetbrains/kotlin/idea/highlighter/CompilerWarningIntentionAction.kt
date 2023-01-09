// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.intention.AbstractEmptyIntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.ui.ExperimentalUI
import org.jetbrains.kotlin.idea.base.fe10.highlighting.KotlinBaseFe10HighlightingBundle
import javax.swing.Icon

class CompilerWarningIntentionAction(private val name: @IntentionFamilyName String): AbstractEmptyIntentionAction(), LowPriorityAction, Iconable {
    override fun getText(): String = KotlinBaseFe10HighlightingBundle.message("kotlin.compiler.warning.0.options", name)

    override fun getFamilyName(): String = name

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as CompilerWarningIntentionAction
        return name == that.name
    }

    override fun hashCode(): Int = name.hashCode()

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon? = if (ExperimentalUI.isNewUI()) null else AllIcons.Actions.RealIntentionBulb
}
