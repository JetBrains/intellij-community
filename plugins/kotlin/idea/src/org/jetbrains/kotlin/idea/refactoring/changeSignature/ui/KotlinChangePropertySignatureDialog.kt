/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.MethodSignatureComponent
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.EditorTextField
import com.intellij.ui.SeparatorFactory
import com.intellij.util.Alarm
import com.intellij.util.ui.FormBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.refactoring.changeSignature.*
import org.jetbrains.kotlin.idea.refactoring.introduce.ui.KotlinSignatureComponent
import org.jetbrains.kotlin.idea.refactoring.validateElement
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValVarKeywordOwner
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.properties.Delegates

class KotlinChangePropertySignatureDialog(
    project: Project,
    private val methodDescriptor: KotlinMethodDescriptor,
    @NlsContexts.Command private val commandName: String?
) : RefactoringDialog(project, true) {
    private val visibilityCombo = ComboBox(
        arrayOf(
            DescriptorVisibilities.INTERNAL,
            DescriptorVisibilities.PRIVATE,
            DescriptorVisibilities.PROTECTED,
            DescriptorVisibilities.PUBLIC,
        )
    )

    private val nameField = EditorTextField(methodDescriptor.name)
    private var returnTypeField: EditorTextField by Delegates.notNull()
    private var receiverTypeCheckBox: JCheckBox? = null
    private var receiverTypeLabel: JLabel by Delegates.notNull()
    private var receiverTypeField: EditorTextField by Delegates.notNull()
    private var receiverDefaultValueLabel: JLabel? = null
    private var receiverDefaultValueField: EditorTextField? = null
    private val updateSignatureAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)
    private var signatureComponent: MethodSignatureComponent by Delegates.notNull()
    private val signatureUpdater = object : DocumentListener, ChangeListener, ActionListener {
        private fun update() = updateSignature()

        override fun documentChanged(event: DocumentEvent) = update()
        override fun stateChanged(e: ChangeEvent?) = update()
        override fun actionPerformed(e: ActionEvent?) = update()
    }

    init {
        title = RefactoringBundle.message("changeSignature.refactoring.name")
        init()
        Disposer.register(myDisposable) {
            updateSignatureAlarm.cancelAllRequests()
        }
    }

    private fun updateSignature(): Unit = SwingUtilities.invokeLater label@{
        if (Disposer.isDisposed(myDisposable)) return@label

        updateSignatureAlarm.cancelAllRequests()
        updateSignatureAlarm.addRequest(
            { PsiDocumentManager.getInstance(myProject).performLaterWhenAllCommitted { updateSignatureAlarmFired() } },
            100,
            ModalityState.stateForComponent(signatureComponent)
        )
    }

    private fun updateSignatureAlarmFired() {
        doUpdateSignature()
    }

    private fun doUpdateSignature() {
        signatureComponent.setSignature(calculateSignature())
    }

    private fun calculateSignature(): String = buildString {
        methodDescriptor.baseDeclaration.safeAs<KtValVarKeywordOwner>()?.valOrVarKeyword?.let {
            val visibility = visibilityCombo.selectedItem
            if (visibility != DescriptorVisibilities.DEFAULT_VISIBILITY) {
                append("$visibility ")
            }

            append("${it.text} ")
        }

        if (receiverTypeCheckBox?.isSelected == true && receiverTypeField.isEnabled) {
            val receiverText = receiverTypeField.text
            if ("->" in receiverText) {
                append("($receiverText).")
            } else {
                append("$receiverText.")
            }
        }

        append("${nameField.text}: ${returnTypeField.text}")
    }

    override fun getPreferredFocusedComponent() = nameField

    override fun createCenterPanel(): JComponent? {
        fun updateReceiverUI() {
            val withReceiver = receiverTypeCheckBox!!.isSelected
            receiverTypeLabel.isEnabled = withReceiver
            receiverTypeField.isEnabled = withReceiver
            receiverDefaultValueLabel?.isEnabled = withReceiver
            receiverDefaultValueField?.isEnabled = withReceiver
        }

        val psiFactory = KtPsiFactory(myProject)
        return with(FormBuilder.createFormBuilder()) {
            val baseDeclaration = methodDescriptor.baseDeclaration
            if (methodDescriptor.canChangeVisibility()) {
                visibilityCombo.selectedItem = methodDescriptor.visibility
                visibilityCombo.addActionListener(signatureUpdater)
                addLabeledComponent(KotlinBundle.message("label.text.visibility"), visibilityCombo)
            }

            nameField.addDocumentListener(signatureUpdater)
            addLabeledComponent(KotlinBundle.message("label.text.name"), nameField)

            val returnTypeCodeFragment = psiFactory.createTypeCodeFragment(
                methodDescriptor.returnTypeInfo.render(),
                baseDeclaration
            )

            returnTypeField = createKotlinEditorTextField(returnTypeCodeFragment).apply { addDocumentListener(signatureUpdater) }
            addLabeledComponent(KotlinBundle.message("label.text.type"), returnTypeField)

            if (baseDeclaration is KtProperty) {
                addSeparator()

                val receiverTypeCheckBox = JCheckBox(KotlinBundle.message("checkbox.text.extension.property")).apply {
                    addActionListener { updateReceiverUI() }
                    addActionListener(signatureUpdater)
                    isSelected = methodDescriptor.receiver != null
                }

                addComponent(receiverTypeCheckBox)
                this@KotlinChangePropertySignatureDialog.receiverTypeCheckBox = receiverTypeCheckBox

                val receiverTypeCodeFragment = psiFactory.createTypeCodeFragment(
                    methodDescriptor.receiverTypeInfo.render(),
                    methodDescriptor.baseDeclaration
                )

                receiverTypeField = createKotlinEditorTextField(receiverTypeCodeFragment).apply { addDocumentListener(signatureUpdater) }
                receiverTypeLabel = JLabel(KotlinBundle.message("label.text.receiver.type"))
                addLabeledComponent(receiverTypeLabel, receiverTypeField)

                if (methodDescriptor.receiver == null) {
                    val receiverDefaultValueCodeFragment = psiFactory.createExpressionCodeFragment("", methodDescriptor.baseDeclaration)
                    receiverDefaultValueField = createKotlinEditorTextField(receiverDefaultValueCodeFragment)
                    receiverDefaultValueLabel = JLabel(KotlinBundle.message("label.text.default.receiver.value"))
                    addLabeledComponent(receiverDefaultValueLabel, receiverDefaultValueField!!)
                }

                updateReceiverUI()
            }

            signatureComponent = KotlinSignatureComponent(calculateSignature(), myProject).apply {
                preferredSize = Dimension(-1, 130)
                minimumSize = Dimension(-1, 130)
            }

            addComponent(SeparatorFactory.createSeparator(RefactoringBundle.message("signature.preview.border.title"), null))
            addComponent(signatureComponent)
            updateSignature()

            panel
        }
    }

    private fun createKotlinEditorTextField(file: PsiFile): EditorTextField = EditorTextField(
        PsiDocumentManager.getInstance(myProject).getDocument(file),
        myProject,
        KotlinFileType.INSTANCE
    )

    private fun getDefaultReceiverValue(): KtExpression? {
        val receiverDefaultValue = receiverDefaultValueField?.text ?: ""
        return if (receiverDefaultValue.isNotEmpty()) KtPsiFactory(myProject).createExpression(receiverDefaultValue) else null
    }

    override fun canRun() {
        val psiFactory = KtPsiFactory(myProject)

        psiFactory.createSimpleName(nameField.text).validateElement(
            KotlinBundle.message("error.text.invalid.name"))
        psiFactory.createType(returnTypeField.text).validateElement(
            KotlinBundle.message("error.text.invalid.return.type"))
        if (receiverTypeCheckBox?.isSelected == true) {
            psiFactory.createType(receiverTypeField.text).validateElement(
                KotlinBundle.message("error.text.invalid.receiver.type"))
        }
        getDefaultReceiverValue()?.validateElement(KotlinBundle.message("error.text.invalid.default.receiver.value"))
    }

    private fun evaluateKotlinChangeInfo(): KotlinChangeInfo {
        val originalDescriptor = methodDescriptor.original

        val receiver = if (receiverTypeCheckBox?.isSelected == true) {
            originalDescriptor.receiver ?: KotlinParameterInfo(
                callableDescriptor = originalDescriptor.baseDescriptor,
                name = "receiver",
                defaultValueForCall = getDefaultReceiverValue()
            )
        } else null

        receiver?.currentTypeInfo = KotlinTypeInfo(false, null, receiverTypeField.text)

        return KotlinChangeInfo(
            originalDescriptor,
            nameField.text,
            KotlinTypeInfo(true, null, returnTypeField.text),
            visibilityCombo.selectedItem as DescriptorVisibility,
            emptyList(),
            receiver,
            originalDescriptor.method,
            checkUsedParameters = true,
        )
    }

    override fun doAction() {
        val changeInfo = evaluateKotlinChangeInfo()
        invokeRefactoring(KotlinChangeSignatureProcessor(myProject, changeInfo, commandName ?: title))
    }

    override fun getHelpId(): String = "refactoring.changeSignature"

    companion object {
        fun createProcessorForSilentRefactoring(
            project: Project,
            @NlsContexts.Command commandName: String,
            descriptor: KotlinMethodDescriptor
        ): BaseRefactoringProcessor {
            val originalDescriptor = descriptor.original
            val changeInfo = KotlinChangeInfo(methodDescriptor = originalDescriptor, context = originalDescriptor.method)
            changeInfo.newName = descriptor.name
            changeInfo.receiverParameterInfo = descriptor.receiver
            return KotlinChangeSignatureProcessor(project, changeInfo, commandName)
        }
    }
}
