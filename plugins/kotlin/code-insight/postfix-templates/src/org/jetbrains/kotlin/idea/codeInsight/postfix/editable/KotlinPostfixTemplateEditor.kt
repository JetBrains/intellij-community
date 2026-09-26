// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix.editable

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateEditorBase
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.codeInsight.postfix.KotlinPostfixTemplatesBundle
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateBooleanExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateExpressionFqnCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNonUnitExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNotNullableExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNullableExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNumberExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateUnitExpressionCondition
import javax.swing.JComponent
import javax.swing.JPanel

internal class KotlinPostfixTemplateEditor(
    provider: PostfixTemplateProvider
) : PostfixTemplateEditorBase<KotlinPostfixTemplateExpressionCondition>(provider, true){

    private val myPanel: JPanel = FormBuilder.createFormBuilder()
        .addComponentFillVertically(myEditTemplateAndConditionsPanel, UIUtil.DEFAULT_VGAP)
        .panel

    override fun fillConditions(group: DefaultActionGroup) {
        group.add(AddConditionAction(KotlinPostfixTemplateUnitExpressionCondition))
        group.add(AddConditionAction(KotlinPostfixTemplateNonUnitExpressionCondition))
        group.add(AddConditionAction(KotlinPostfixTemplateBooleanExpressionCondition))
        group.add(AddConditionAction(KotlinPostfixTemplateNumberExpressionCondition))
        group.add(AddConditionAction(KotlinPostfixTemplateNullableExpressionCondition))
        group.add(AddConditionAction(KotlinPostfixTemplateNotNullableExpressionCondition))
        for (project in ProjectManager.getInstance().openProjects) {
            group.add(ChooseClassAction(project))
        }
        group.add(ChooseClassAction(null))
    }

    private inner class ChooseClassAction(private val project: Project?) : DumbAwareAction(
        if (project != null && !project.isDefault)
            KotlinPostfixTemplatesBundle.message("action.text.choose.class.in.0", project.name)
        else
            KotlinPostfixTemplatesBundle.message("action.text.enter.class.name")
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val fqn = getFqn() ?: return
            myExpressionTypesListModel.addElement(KotlinPostfixTemplateExpressionFqnCondition(fqn))
        }

        private fun getFqn(): String? {
            val title = KotlinPostfixTemplatesBundle.message("postfix.template.editor.choose.class.title")
            if (project == null || project.isDefault) {
                return Messages.showInputDialog(
                    myPanel,
                    KotlinPostfixTemplatesBundle.message("label.enter.fully.qualified.class.name"),
                    title,
                    null
                )
            }
            val chooser = TreeClassChooserFactory.getInstance(project).createAllProjectScopeChooser(title)
            chooser.showDialog()
            return chooser.selected?.qualifiedName
        }
    }

    override fun createTemplate(
        templateId: String,
        templateName: String
    ): PostfixTemplate {
        val conditions = LinkedHashSet(myExpressionTypesListModel.elements().toList())
        val templateText = myTemplateEditor.getDocument().text
        return KotlinEditablePostfixTemplate(
            templateId = templateId,
            templateName = templateName,
            templateText = templateText,
            example = "",
            expressionConditions = conditions,
            useTopmostExpression = myApplyToTheTopmostJBCheckBox.isSelected,
            provider = myProvider
        )
    }

    override fun getComponent(): JComponent = myPanel
}