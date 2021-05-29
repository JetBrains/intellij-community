// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiReference
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.inline.InlineOptionsDialog
import com.intellij.usageView.UsageViewTypeLocation
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSettings
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import kotlin.reflect.KMutableProperty1

abstract class AbstractKotlinInlineDialog<TDeclaration : KtNamedDeclaration>(
    protected val declaration: TDeclaration,
    protected val reference: PsiReference?,
    protected val editor: Editor?,
) : InlineOptionsDialog(declaration.project, true, declaration) {
    final override fun isKeepTheDeclarationByDefault(): Boolean = inlineKeepOption.get(KotlinRefactoringSettings.instance)
    final override fun isInlineThis(): Boolean = inlineThisOption.get(KotlinRefactoringSettings.instance)
    public final override fun doAction() {
        invokeRefactoring(createProcessor())
        saveSettings()
    }

    private fun saveSettings() {
        val settings = KotlinRefactoringSettings.instance
        if (myRbInlineThisOnly.isEnabled && myRbInlineAll.isEnabled) {
            inlineThisOption.set(settings, isInlineThisOnly)
        }

        if (myKeepTheDeclaration?.isEnabled == true) {
            inlineKeepOption.set(settings, isKeepTheDeclaration)
        }
    }

    abstract val inlineThisOption: KMutableProperty1<KotlinRefactoringSettings, Boolean>
    abstract val inlineKeepOption: KMutableProperty1<KotlinRefactoringSettings, Boolean>
    abstract fun createProcessor(): BaseRefactoringProcessor

    // NB: can be -1 in case of too expensive search!
    protected val occurrencesNumber = initOccurrencesNumber(declaration)

    private val occurrencesString
        get() = if (occurrencesNumber >= 0) {
            "" + occurrencesNumber + " " + StringUtil.pluralize("occurrence", occurrencesNumber)
        } else null

    private val kind: String get() = ElementDescriptionUtil.getElementDescription(declaration, UsageViewTypeLocation.INSTANCE)

    private val refactoringName get() = KotlinBundle.message("text.inline.0", StringUtil.capitalizeWords(kind, true))

    init {
        myInvokedOnReference = reference != null
        title = refactoringName
    }

    // If this is true, "inline all & remove" is disabled,
    // "inline all and keep" is disabled on references and enabled on original declaration.
    // Independent on this, "inline this only" is enabled on references and disabled on original declaration
    // If this is false, "inline all & remove" is dependent on next flag (allowInlineAll),
    // "inline all and keep" is enabled
    override fun canInlineThisOnly() = false

    // If this is false, "inline all & remove" is disabled
    // If this is true, it can be enabled if 'canInlineThisOnly' is false (see above)
    override fun allowInlineAll() = true

    override fun getBorderTitle() = refactoringName

    override fun getNameLabelText(): String {
        val occurrencesString = occurrencesString?.let { " - $it" } ?: ""
        return "${kind.capitalize()} ${declaration.nameAsSafeName} $occurrencesString"
    }

    @Nls
    private fun getInlineText(verb: String) = KotlinBundle.message(
        "text.inline.all.references.and.verb.0.the.kind.1.occurrences.2",
        verb,
        kind,
        (occurrencesString?.let { "($it)" } ?: "")
    )

    override fun getInlineAllText(): String =
        if (declaration.isWritable)
            getInlineText(KotlinBundle.message("text.remove"))
        else
            RefactoringBundle.message("all.invocations.in.project")

    override fun getKeepTheDeclarationText(): String? =
        // With non-writable callable refactoring does not work anyway (for both property or function)
        if (declaration.isWritable && (occurrencesNumber > 1 || !myInvokedOnReference)) {
            getInlineText(KotlinBundle.message("text.keep"))
        } else {
            null
        }

    override fun getInlineThisText() = KotlinBundle.message("text.inline.this.reference.and.keep.the.0", kind)
}
