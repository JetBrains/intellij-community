// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateEditor
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateExpressionCondition
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiFile
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinEditablePostfixTemplate
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateEditor
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateBooleanExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateExpressionFqnCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNotNullableExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNonUnitExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNullableExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNumberExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateUnitExpressionCondition
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtPsiFactory

// K2 PostfixTemplateProvider
@ApiStatus.Internal
class KotlinPostfixTemplateProvider : PostfixTemplateProvider {
    private val templateSet: Set<PostfixTemplate> by lazy {
        setOf(
            // shared
            KtIfExpressionPostfixTemplate(this),
            KtElseExpressionPostfixTemplate(this),
            // K2
            KotlinParenthesizedPostfixTemplate(this),
            KotlinAssertPostfixTemplate(this),
            KotlinSystemOutPostfixTemplate(this),
            KotlinWithPostfixTemplate(this),
            KotlinWhilePostfixTemplate(this),
            KotlinReturnPostfixTemplate(this),
            KotlinSpreadPostfixTemplate(this),
            KotlinForDestructuringPostfixTemplate(this),
            KotlinForPostfixTemplate(this),
            KotlinIterPostfixTemplate(this),
            KotlinItorPostfixTemplate(this),
            KotlinForReversedPostfixTemplate(this),
            KotlinForWithIndexPostfixTemplate(this),
            KotlinForLoopNumbersPostfixTemplate(this),
            KotlinForLoopReverseNumbersPostfixTemplate(this),
            KotlinWrapIntoListPostfixTemplate(this),
            KotlinWrapIntoSetPostfixTemplate(this),
            KotlinWrapIntoArrayPostfixTemplate(this),
            KotlinWrapIntoSequencePostfixTemplate(this),
            KotlinIfPostfixTemplate(this),
            KotlinUnlessPostfixTemplate(this),
            KotlinNotNullPostfixTemplate(this),
            KotlinNnPostfixTemplate(this),
            KotlinNullPostfixTemplate(this),
            KotlinTryPostfixTemplate(this),
            KotlinWhenPostfixTemplate(this),
            KotlinNotPostfixTemplate(this),
            KotlinValPostfixTemplate(this),
            KotlinVarPostfixTemplate(this),
            KotlinArgumentPostfixTemplate(this),
        )
    }

    override fun createEditor(templateToEdit: PostfixTemplate?): PostfixTemplateEditor? {
        if (templateToEdit == null || templateToEdit is KotlinEditablePostfixTemplate) {
            val editor = KotlinPostfixTemplateEditor(this)
            editor.setTemplate(templateToEdit)
            return editor
        }
        return null
    }

    override fun getPresentableName(): String =
        KotlinPostfixTemplatesBundle.message("kotlin.postfix.template.provider.name")

    override fun getTemplates(): Set<PostfixTemplate> {
        return templateSet
    }

    override fun isTerminalSymbol(currentChar: Char): Boolean = currentChar == '.' || currentChar == '!'

    override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int): PsiFile {
        val originalFile = copyFile.originalFile
        if (originalFile !is KtBlockCodeFragment) {
            return copyFile
        }
        val codeFragment = KtBlockCodeFragment(
            project = originalFile.project,
            name = "fragment.kt",
            text = originalFile.text,
            imports = originalFile.importsToString(),
            context = originalFile.context
        )
        codeFragment.setOriginalFile(originalFile)
        return codeFragment
    }

    override fun preExpand(file: PsiFile, editor: Editor) {}
    override fun afterExpand(file: PsiFile, editor: Editor) {}

    override fun writeExternalTemplate(
        template: PostfixTemplate,
        parentElement: Element
    ) {
        if (template !is KotlinEditablePostfixTemplate) return

        // Note: if custom properties are added, these need to be written here too
        PostfixTemplatesUtils.writeExternalTemplate(template, parentElement)
    }

    override fun readExternalTemplate(
        id: @NonNls String,
        name: @NlsSafe String,
        template: Element
    ): PostfixTemplate? {
        val liveTemplate = PostfixTemplatesUtils.readExternalLiveTemplate(template, this) ?: return null
        val conditions = PostfixTemplatesUtils.readExternalConditions(template, ::readCondition).filterNotNull().toSet()

        // Note: if custom properties are added, these need to be read here too
        val useTopmostExpression = PostfixTemplatesUtils.readExternalTopmostAttribute(template)

        return KotlinEditablePostfixTemplate(
            templateId = id,
            templateName = name,
            liveTemplate = liveTemplate,
            example = "",
            expressionConditions = conditions,
            useTopmostExpression = useTopmostExpression,
            provider = this
        )
    }

    private fun readCondition(condition: Element): KotlinPostfixTemplateExpressionCondition? {
        val id = condition.getAttributeValue(PostfixTemplateExpressionCondition.ID_ATTR)

        return when (id) {
            KotlinPostfixTemplateUnitExpressionCondition.id -> KotlinPostfixTemplateUnitExpressionCondition
            KotlinPostfixTemplateNonUnitExpressionCondition.id -> KotlinPostfixTemplateNonUnitExpressionCondition
            KotlinPostfixTemplateBooleanExpressionCondition.id -> KotlinPostfixTemplateBooleanExpressionCondition
            KotlinPostfixTemplateNumberExpressionCondition.id -> KotlinPostfixTemplateNumberExpressionCondition
            KotlinPostfixTemplateNullableExpressionCondition.id -> KotlinPostfixTemplateNullableExpressionCondition
            KotlinPostfixTemplateNotNullableExpressionCondition.id -> KotlinPostfixTemplateNotNullableExpressionCondition
            KotlinPostfixTemplateExpressionFqnCondition.ID -> {
                val fqn = condition.getAttributeValue(KotlinPostfixTemplateExpressionFqnCondition.FQN_ATTR)
                fqn?.let { KotlinPostfixTemplateExpressionFqnCondition(it) }
            }
            else -> null
        }
    }
}
