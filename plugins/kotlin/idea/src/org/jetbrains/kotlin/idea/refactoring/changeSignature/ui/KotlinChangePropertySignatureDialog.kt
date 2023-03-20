// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.options.ConfigurationException
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
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.util.Alarm
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.AddFullQualifierIntention
import org.jetbrains.kotlin.idea.refactoring.changeSignature.*
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinChangeSignatureDialog.Companion.getTypeInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinChangeSignatureDialog.Companion.showWarningMessage
import org.jetbrains.kotlin.idea.refactoring.introduce.ui.KotlinSignatureComponent
import org.jetbrains.kotlin.idea.refactoring.validateElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValVarKeywordOwner
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

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

    private val signatureUpdater = object : DocumentListener, ChangeListener, ActionListener {
        private fun update() = updateSignature()

        override fun documentChanged(event: DocumentEvent) = update()
        override fun stateChanged(e: ChangeEvent?) = update()
        override fun actionPerformed(e: ActionEvent?) = update()
    }

    private val kotlinPsiFactory = KtPsiFactory(project)
    private val returnTypeCodeFragment = kotlinPsiFactory.createTypeCodeFragment(
        methodDescriptor.returnTypeInfo.render(),
        methodDescriptor.baseDeclaration,
    )

    private val receiverTypeCodeFragment = kotlinPsiFactory.createTypeCodeFragment(
        methodDescriptor.receiverTypeInfo.render(),
        methodDescriptor.baseDeclaration,
    )

    private val receiverDefaultValueCodeFragment = kotlinPsiFactory.createExpressionCodeFragment(
        "",
        methodDescriptor.baseDeclaration,
    )

    private val nameField = EditorTextField(methodDescriptor.name).apply { addDocumentListener(signatureUpdater) }
    private val name: String get() = nameField.text.quoteIfNeeded()

    private val returnTypeField = createKotlinEditorTextField(returnTypeCodeFragment, withListener = true)
    private val receiverTypeField = createKotlinEditorTextField(receiverTypeCodeFragment, withListener = true)
    private val receiverDefaultValueField = createKotlinEditorTextField(receiverDefaultValueCodeFragment, withListener = false)

    private var receiverTypeCheckBox: JCheckBox? = null

    private val updateSignatureAlarm = Alarm()
    private val signatureComponent: MethodSignatureComponent = KotlinSignatureComponent("", project).apply {
        preferredSize = Dimension(-1, 130)
        minimumSize = Dimension(-1, 130)
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
        validateButtons()
    }

    private fun doUpdateSignature() {
        signatureComponent.setSignature(calculateSignature())
    }

    private fun calculateSignature(): String = buildString {
        methodDescriptor.baseDeclaration.safeAs<KtValVarKeywordOwner>()?.valOrVarKeyword?.let {
            val visibility = if (methodDescriptor.canChangeVisibility()) visibilityCombo.selectedItem else methodDescriptor.visibility
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

        append("$name: ${returnTypeField.text}")
    }

    override fun getPreferredFocusedComponent() = nameField

    override fun createCenterPanel(): JComponent = panel {
        if (methodDescriptor.canChangeVisibility()) {
            row(KotlinBundle.message("label.text.visibility")) {
                cell(visibilityCombo)
                    .applyToComponent {
                        selectedItem = methodDescriptor.visibility
                        addActionListener(signatureUpdater)
                    }
            }
        }

        row(KotlinBundle.message("label.text.name")) { cell(nameField).align(AlignX.FILL) }
        row(KotlinBundle.message("label.text.type")) { cell(returnTypeField).align(AlignX.FILL) }

        if (methodDescriptor.baseDeclaration is KtProperty) {
            row {
                receiverTypeCheckBox = checkBox(KotlinBundle.message("checkbox.text.extension.property"))
                    .applyToComponent {
                        addActionListener(signatureUpdater)
                        isSelected = methodDescriptor.receiver != null
                    }.component
            }

            row(KotlinBundle.message("label.text.receiver.type")) {
                cell(receiverTypeField).align(AlignX.FILL)
            }.enabledIf(receiverTypeCheckBox!!.selected)

            if (methodDescriptor.receiver == null) {
                row(KotlinBundle.message("label.text.default.receiver.value")) {
                    cell(receiverDefaultValueField).align(AlignX.FILL)
                }.enabledIf(receiverTypeCheckBox!!.selected)
            }
        }

        group(RefactoringBundle.message("signature.preview.border.title"), indent = false) {
            row { cell(signatureComponent).align(AlignX.FILL) }
        }

        updateSignature()
    }

    private fun createKotlinEditorTextField(file: PsiFile, withListener: Boolean): EditorTextField = EditorTextField(
        PsiDocumentManager.getInstance(myProject).getDocument(file),
        myProject,
        KotlinFileType.INSTANCE
    ).apply {
        if (withListener) addDocumentListener(signatureUpdater)
    }

    override fun canRun() {
        if (!name.isIdentifier()) {
            throw ConfigurationException(KotlinBundle.message("error.text.invalid.name"))
        }

        returnTypeCodeFragment.validateElement(KotlinBundle.message("error.text.invalid.return.type"))
        if (receiverTypeCheckBox?.isSelected == true) {
            receiverTypeCodeFragment.validateElement(KotlinBundle.message("error.text.invalid.receiver.type"))
        }
    }

    private fun evaluateKotlinChangeInfo(): KotlinChangeInfo {
        val originalDescriptor = methodDescriptor.original

        val receiver = if (receiverTypeCheckBox?.isSelected == true) {
            originalDescriptor.receiver ?: KotlinParameterInfo(
                callableDescriptor = originalDescriptor.baseDescriptor,
                name = "receiver",
                defaultValueForCall = receiverDefaultValueCodeFragment.getContentElement(),
            )
        } else null

        receiver?.currentTypeInfo = receiverTypeCodeFragment.getTypeInfo(isCovariant = false, forPreview = false)
        return KotlinChangeInfo(
            originalDescriptor,
            name,
            returnTypeCodeFragment.getTypeInfo(isCovariant = false, forPreview = false),
            if (methodDescriptor.canChangeVisibility()) visibilityCombo.selectedItem as DescriptorVisibility else methodDescriptor.visibility,
            emptyList(),
            receiver,
            originalDescriptor.method,
            checkUsedParameters = true,
        )
    }

    override fun doAction() {
        val changeInfo = evaluateKotlinChangeInfo()
        val typeInfo = changeInfo.newReturnTypeInfo
        if (typeInfo.type == null && !showWarningMessage(
                myProject,
                KotlinBundle.message("message.text.property.type.cannot.be.resolved", typeInfo.render()),
            )
        ) return

        val receiverParameterInfo = changeInfo.receiverParameterInfo
        val receiverTypeInfo = receiverParameterInfo?.currentTypeInfo
        if (receiverTypeInfo != null && receiverTypeInfo.type == null && !showWarningMessage(
                myProject,
                KotlinBundle.message("message.text.property.receiver.type.cannot.be.resolved", receiverTypeInfo.render()),
            )
        ) return

        receiverParameterInfo?.let { normalizeReceiver(it, withCopy = false) }
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
            changeInfo.receiverParameterInfo = descriptor.receiver?.also { normalizeReceiver(it, withCopy = true) }
            return KotlinChangeSignatureProcessor(project, changeInfo, commandName)
        }

        private fun normalizeReceiver(receiver: KotlinParameterInfo, withCopy: Boolean) {
            val defaultValue = receiver.defaultValueForCall ?: return
            val newElement = if (withCopy) {
                val fragment = KtPsiFactory(defaultValue.project).createExpressionCodeFragment(defaultValue.text, defaultValue)
                fragment.getContentElement() ?: return
            } else {
                defaultValue
            }

            receiver.defaultValueForCall = AddFullQualifierIntention.addQualifiersRecursively(newElement) as? KtExpression
        }
    }
}
