// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.actions.generate

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.generation.ui.AbstractGenerateEqualsWizard
import com.intellij.ide.wizard.StepAdapter
import com.intellij.java.JavaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.refactoring.classMembers.AbstractMemberInfoModel
import com.intellij.ui.NonFocusableCheckBox
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.core.isInheritable
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberSelectionPanel
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.utils.keysToMap
import javax.swing.JLabel
import javax.swing.JPanel

class KotlinGenerateEqualsWizard(
    project: Project,
    klass: KtClass,
    properties: List<KtNamedDeclaration>,
    needEquals: Boolean,
    needHashCode: Boolean
) : AbstractGenerateEqualsWizard<KtClass, KtNamedDeclaration, KotlinMemberInfo>(
    project, BuilderImpl(klass, properties, needEquals, needHashCode)
) {
    private object MemberInfoModelImpl : AbstractMemberInfoModel<KtNamedDeclaration, KotlinMemberInfo>()

    private class BuilderImpl(
        private val klass: KtClass,
        properties: List<KtNamedDeclaration>,
        needEquals: Boolean,
        needHashCode: Boolean
    ) : AbstractGenerateEqualsWizard.Builder<KtClass, KtNamedDeclaration, KotlinMemberInfo>() {
        private val equalsPanel: KotlinMemberSelectionPanel?
        private val hashCodePanel: KotlinMemberSelectionPanel?

        private val memberInfos = properties.map { createMemberInfo(it) }

        private val membersToHashCode = HashMap(properties.keysToMap { createMemberInfo(it) })

        init {
            equalsPanel = if (needEquals) {
                KotlinMemberSelectionPanel(KotlinBundle.message("action.generate.equals.choose.equals"), memberInfos).apply {
                    table.memberInfoModel = MemberInfoModelImpl
                }
            } else null

            hashCodePanel = if (needHashCode) {
                KotlinMemberSelectionPanel(KotlinBundle.message("action.generate.equals.choose.hashcode"), memberInfos).apply {
                    table.memberInfoModel = MemberInfoModelImpl
                }
            } else null
        }

        private fun createMemberInfo(it: KtNamedDeclaration): KotlinMemberInfo {
            return KotlinMemberInfo(it).apply {
                val descriptor = it.unsafeResolveToDescriptor()
                isChecked = (descriptor as? PropertyDescriptor)?.getter?.isDefault ?: true
            }
        }

        override fun getPsiClass() = klass

        override fun getClassFields() = memberInfos

        override fun getFieldsToHashCode() = membersToHashCode

        override fun getFieldsToNonNull() = HashMap<KtNamedDeclaration, KotlinMemberInfo>()

        override fun getEqualsPanel() = equalsPanel

        override fun getHashCodePanel() = hashCodePanel

        override fun getNonNullPanel() = null

        override fun updateHashCodeMemberInfos(equalsMemberInfos: MutableCollection<out KotlinMemberInfo>) {
            hashCodePanel?.table?.setMemberInfos(equalsMemberInfos.map { membersToHashCode[it.member] })
        }

        override fun updateNonNullMemberInfos(equalsMemberInfos: MutableCollection<out KotlinMemberInfo>?) {

        }
    }

    private object OptionsStep : StepAdapter() {
        private val panel = JPanel(VerticalFlowLayout())

        init {
            with(NonFocusableCheckBox(JavaBundle.message("generate.equals.hashcode.accept.sublcasses"))) {
                isSelected = CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER
                addActionListener { CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER = isSelected }
                panel.add(this)
            }
            panel.add(JLabel(JavaBundle.message("generate.equals.hashcode.accept.sublcasses.explanation")))
        }

        override fun getComponent() = panel
    }

    override fun addSteps() {
        if (myEqualsPanel != null && myClass.isInheritable()) {
            addStep(OptionsStep)
        }
        super.addSteps()
    }

    override fun doOKAction() {
        myEqualsPanel?.let { updateHashCodeMemberInfos(it.table.selectedMemberInfos) }
        super.doOKAction()
    }

    fun getPropertiesForEquals() = myEqualsPanel?.table?.selectedMemberInfos?.map { it.member } ?: emptyList()

    fun getPropertiesForHashCode() = myHashCodePanel?.table?.selectedMemberInfos?.map { it.member } ?: emptyList()
}