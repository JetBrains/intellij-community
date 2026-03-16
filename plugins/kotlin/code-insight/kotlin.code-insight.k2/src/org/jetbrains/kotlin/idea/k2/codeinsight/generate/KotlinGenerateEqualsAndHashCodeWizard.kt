// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.codeInsight.generation.ui.TemplateChooserStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateEqualsWizard
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.DefaultMemberFilters
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.KotlinEqualsHashCodeGeneratorExtension
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.MemberFilters
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isInheritable
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.JCheckBox
import javax.swing.JComponent

class KotlinGenerateEqualsAndHashCodeWizard(
    project: Project,
    klass: KtClass,
    properties: List<KtNamedDeclaration>,
    needEquals: Boolean,
    needHashCode: Boolean,
    private val memberInfos: List<KotlinMemberInfo>,
    private val membersToHashCode: HashMap<KtNamedDeclaration, KotlinMemberInfo>
) : KotlinGenerateEqualsWizard(project, klass, properties, needEquals, needHashCode, memberInfos, membersToHashCode) {
    override fun addSteps() {
        addStep(object : TemplateChooserStep(myClass, KotlinEqualsHashCodeTemplatesManager.getInstance()) {
            override fun setErrorText(
                errorText: @NlsContexts.DialogMessage String?,
                component: JComponent?
            ) {
                this@KotlinGenerateEqualsAndHashCodeWizard.setErrorText(errorText, component)
            }

            override fun isDisposed(): Boolean = this@KotlinGenerateEqualsAndHashCodeWizard.isDisposed

            override fun appendAdditionalOptions(stepPanel: JComponent) {
                if (myEqualsPanel != null && myClass.isInheritable()) {
                    super.appendAdditionalOptions(stepPanel)
                }
            }

            override fun createUseGettersInsteadOfFieldsCheckbox(): JCheckBox? = null

            override fun _commit(finishChosen: Boolean) {
                val selectedTemplate = this.selectedTemplate
                val ext = KotlinEqualsHashCodeGeneratorExtension.getSingleApplicableFor(myClass)

                if (ext == null || !ext.isExtensionTemplate(selectedTemplate)) {
                    enableTables()
                    updateMemberInfos(myClass, DefaultMemberFilters)
                } else {
                    disableTables()
                    updateMemberInfos(myClass, ext.memberFilters)
                }
                super._commit(finishChosen)
            }

            private fun updateMemberInfos(ktClass: KtClass, memberFilters: MemberFilters) {
                myEqualsPanel.table.setMemberInfos(
                    memberInfos.filter { memberFilters.isApplicableForEqualsInClass(it.member, ktClass) }
                )
                membersToHashCode.values.forEach { memberInfo ->
                    if (!memberFilters.isApplicableForHashCodeInClass(memberInfo.member, ktClass)) {
                        memberInfo.isChecked = false
                    }
                }

            }
        })
        chooserSteps() //skip options step
    }

    private fun enableTables() {
        setTableState(true)
    }

    private fun disableTables() {
        setTableState(false)
    }

    private fun setTableState(isEnabled: Boolean) {
        myEqualsPanel.table.isEnabled = isEnabled
        myHashCodePanel.table.isEnabled = isEnabled
    }
}
