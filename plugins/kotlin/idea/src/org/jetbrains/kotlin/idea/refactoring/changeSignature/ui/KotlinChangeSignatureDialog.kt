// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.observable.util.addItemListener
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ChangeSignatureDialogBase
import com.intellij.refactoring.changeSignature.MethodDescriptor
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.table.EditorTextFieldJBTableRowRenderer
import com.intellij.util.ui.table.JBTableRow
import com.intellij.util.ui.table.JBTableRowEditor
import com.intellij.util.ui.table.JBTableRowRenderer
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.caches.resolve.CodeFragmentAnalyzer
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.intentions.AddFullQualifierIntention
import org.jetbrains.kotlin.idea.refactoring.changeSignature.*
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinMethodDescriptor.Kind
import org.jetbrains.kotlin.idea.refactoring.introduce.ui.KotlinSignatureComponent
import org.jetbrains.kotlin.idea.refactoring.rename.findElementForRename
import org.jetbrains.kotlin.idea.refactoring.validateElement
import org.jetbrains.kotlin.psi.KtExpressionCodeFragment
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeCodeFragment
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.isError
import java.awt.Font
import java.awt.Toolkit
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.*

class KotlinChangeSignatureDialog(
    project: Project,
    val editor: Editor?,
    methodDescriptor: KotlinMethodDescriptor,
    context: PsiElement,
    @NlsContexts.Command private val commandName: String?,
) : ChangeSignatureDialogBase<
        KotlinParameterInfo,
        PsiElement,
        DescriptorVisibility,
        KotlinMethodDescriptor,
        ParameterTableModelItemBase<KotlinParameterInfo>,
        KotlinCallableParameterTableModel>(project, methodDescriptor, false, context) {
    override fun getFileType(): KotlinFileType = KotlinFileType.INSTANCE

    override fun createParametersInfoModel(descriptor: KotlinMethodDescriptor) =
        createParametersInfoModel(descriptor, myDefaultValueContext)

    override fun createReturnTypeCodeFragment() = createReturnTypeCodeFragment(myProject, myMethod)

    private val parametersTableModel: KotlinCallableParameterTableModel get() = super.myParametersTableModel

    override fun createParametersListTable(): ParametersListTable = object : ParametersListTable() {
        private val rowRenderer = object : EditorTextFieldJBTableRowRenderer(project, KotlinLanguage.INSTANCE, disposable) {
            override fun getText(table: JTable?, row: Int): String {
                val item = getRowItem(row)
                val valOrVar = if (myMethod.kind === Kind.PRIMARY_CONSTRUCTOR) {
                    when (item.parameter.valOrVar) {
                        KotlinValVar.None -> "    "
                        KotlinValVar.Val -> "val "
                        KotlinValVar.Var -> "var "
                    }
                } else {
                    ""
                }

                val parameterName = getPresentationName(item)
                val typeText = item.typeCodeFragment.text
                val defaultValue = if (item.isReceiverIn(parametersTableModel) || !item.parameter.defaultValueAsDefaultParameter)
                    item.defaultValueCodeFragment.text
                else
                    ""

                val separator = StringUtil.repeatSymbol(' ', getParamNamesMaxLength() - parameterName.length + 1)
                val text = "$valOrVar$parameterName:$separator$typeText" + if (StringUtil.isNotEmpty(defaultValue)) {
                    KotlinBundle.message("text.default.value", defaultValue)
                } else {
                    ""
                }

                return " $text"
            }

        }

        override fun getRowRenderer(row: Int): JBTableRowRenderer = rowRenderer

        override fun getRowEditor(item: ParameterTableModelItemBase<KotlinParameterInfo>): JBTableRowEditor = object : JBTableRowEditor() {
            private val components = ArrayList<JComponent>()
            private val nameEditor = EditorTextField(item.parameter.name, project, fileType)
            private val defaultParameterCheckbox = JCheckBox()

            private fun notifyReceiverListeners() {
                val isNotReceiver = !item.isReceiverIn(parametersTableModel)
                nameEditor.isEnabled = isNotReceiver
                defaultParameterCheckbox.isEnabled = isNotReceiver
            }

            private fun isDefaultColumnEnabled() = item.parameter.isNewParameter && item.parameter != myMethod.receiver

            override fun prepareEditor(table: JTable, row: Int) {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                var column = 0

                for (columnInfo in parametersTableModel.columnInfos) {
                    val panel = JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 4, 2, true, false))
                    val editor: EditorTextField?
                    val component: JComponent
                    val columnFinal = column

                    if (KotlinCallableParameterTableModel.isTypeColumn(columnInfo)) {
                        val document = PsiDocumentManager.getInstance(project).getDocument(item.typeCodeFragment)
                        editor = EditorTextField(document, project, fileType)
                        component = editor
                    } else if (KotlinCallableParameterTableModel.isNameColumn(columnInfo)) {
                        editor = nameEditor
                        component = editor
                        notifyReceiverListeners()
                    } else if (KotlinCallableParameterTableModel.isDefaultValueColumn(columnInfo) && isDefaultColumnEnabled()) {
                        val document = PsiDocumentManager.getInstance(project).getDocument(item.defaultValueCodeFragment)
                        editor = EditorTextField(document, project, fileType)
                        component = editor
                    } else if (KotlinCallableParameterTableModel.isDefaultParameterColumn(columnInfo) && isDefaultColumnEnabled()) {
                        defaultParameterCheckbox.isSelected = item.parameter.defaultValue != null
                        defaultParameterCheckbox.addItemListener(
                            disposable,
                            ItemListener {
                                parametersTableModel.setValueAtWithoutUpdate(it.stateChange == ItemEvent.SELECTED, row, columnFinal)
                                updateSignature()
                            },
                        )

                        component = defaultParameterCheckbox
                        editor = null
                        notifyReceiverListeners()
                    } else if (KotlinPrimaryConstructorParameterTableModel.isValVarColumn(columnInfo)) {
                        val comboBox = ComboBox(KotlinValVar.values())
                        comboBox.selectedItem = item.parameter.valOrVar
                        comboBox.addItemListener(
                            disposable,
                            ItemListener {
                                parametersTableModel.setValueAtWithoutUpdate(it.item, row, columnFinal)
                                updateSignature()
                            },
                        )

                        component = comboBox
                        editor = null
                    } else if (KotlinFunctionParameterTableModel.isReceiverColumn(columnInfo)) {
                        val checkBox = JCheckBox()
                        checkBox.isSelected = parametersTableModel.receiver == item.parameter
                        checkBox.addItemListener(
                            disposable,
                            ItemListener {
                                val newReceiver = if (it.stateChange == ItemEvent.SELECTED) item.parameter else null
                                (parametersTableModel as KotlinFunctionParameterTableModel).receiver = newReceiver
                                updateSignature()
                                notifyReceiverListeners()
                            },
                        )

                        component = checkBox
                        editor = null
                    } else
                        continue

                    val label = JBLabel(columnInfo.name, UIUtil.ComponentStyle.SMALL)
                    panel.add(label)

                    if (editor != null) {
                        val listener = RowEditorChangeListener(columnFinal)
                        editor.addDocumentListener(listener)
                        Disposer.register(disposable) {
                            editor.removeDocumentListener(listener)
                        }

                        editor.setPreferredWidth(table.width / parametersTableModel.columnCount)
                    }

                    components.add(component)
                    panel.add(component)
                    add(panel)
                    column++
                }
            }

            override fun getValue(): JBTableRow = JBTableRow { column ->
                val columnInfo = parametersTableModel.columnInfos[column]
                when {
                    KotlinPrimaryConstructorParameterTableModel.isValVarColumn(columnInfo) -> (components[column] as JComboBox<*>).selectedItem
                    KotlinCallableParameterTableModel.isTypeColumn(columnInfo) -> item.typeCodeFragment
                    KotlinCallableParameterTableModel.isNameColumn(columnInfo) -> (components[column] as EditorTextField).text
                    KotlinCallableParameterTableModel.isDefaultValueColumn(columnInfo) -> item.defaultValueCodeFragment
                    KotlinCallableParameterTableModel.isDefaultParameterColumn(columnInfo) -> item.parameter.defaultValue != null
                    else -> null
                }
            }

            private fun getColumnWidth(letters: Int): Int {
                var font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
                font = Font(font.fontName, font.style, 12)
                return letters * Toolkit.getDefaultToolkit().getFontMetrics(font).stringWidth("W")
            }

            private fun getEditorIndex(x: Int): Int {
                @Suppress("NAME_SHADOWING") var x = x

                val columnLetters = if (isDefaultColumnEnabled())
                    intArrayOf(4, getParamNamesMaxLength(), getTypesMaxLength(), getDefaultValuesMaxLength())
                else
                    intArrayOf(4, getParamNamesMaxLength(), getTypesMaxLength())

                var columnIndex = 0
                for (i in (if (myMethod.kind === Kind.PRIMARY_CONSTRUCTOR) 0 else 1) until columnLetters.size) {
                    val width = getColumnWidth(columnLetters[i])

                    if (x <= width)
                        return columnIndex

                    columnIndex++
                    x -= width
                }

                return columnIndex - 1
            }

            override fun getPreferredFocusedComponent(): JComponent {
                val me = mouseEvent
                val index = when {
                    me != null -> getEditorIndex(me.point.getX().toInt())
                    myMethod.kind === Kind.PRIMARY_CONSTRUCTOR -> 1
                    else -> 0
                }
                val component = components[index]
                return if (component is EditorTextField) component.focusTarget else component
            }

            override fun getFocusableComponents(): Array<JComponent> = Array(components.size) {
                val component = components[it]
                (component as? EditorTextField)?.focusTarget ?: component
            }
        }

        override fun isRowEmpty(row: Int): Boolean {
            val rowItem = getRowItem(row)
            if (rowItem.parameter.name.isNotEmpty()) return false
            if (rowItem.parameter.typeText.isNotEmpty()) return false
            return true
        }
    }

    private fun getPresentationName(item: ParameterTableModelItemBase<KotlinParameterInfo>): String {
        val parameter = item.parameter
        return if (parameter == parametersTableModel.receiver) "<receiver>" else parameter.name
    }

    private fun getColumnTextMaxLength(nameFunction: Function1<ParameterTableModelItemBase<KotlinParameterInfo>, String?>) =
        parametersTableModel.items.maxOfOrNull { nameFunction(it)?.length ?: 0 } ?: 0

    private fun getParamNamesMaxLength() = getColumnTextMaxLength { getPresentationName(it) }

    private fun getTypesMaxLength() = getColumnTextMaxLength { it.typeCodeFragment?.text }

    private fun getDefaultValuesMaxLength() = getColumnTextMaxLength { it.defaultValueCodeFragment?.text }

    override fun isListTableViewSupported() = true

    override fun createCallerChooser(@NlsContexts.DialogTitle title: String, treeToReuse: Tree?, callback: Consumer<in Set<PsiElement>>) =
        KotlinCallerChooser(myMethod.method, myProject, title, treeToReuse, callback)

    // Forbid receiver propagation
    override fun mayPropagateParameters() = parameters.any { it.isNewParameter && it != parametersTableModel.receiver }

    override fun calculateSignature(): String = evaluateChangeInfo(
        parametersTableModel,
        myReturnTypeCodeFragment,
        getMethodDescriptor(),
        visibility,
        methodName,
        myDefaultValueContext,
        true
    ).getNewSignature(getMethodDescriptor().originalPrimaryCallable)

    override fun createVisibilityControl() = ComboBoxVisibilityPanel(
        arrayOf(
            DescriptorVisibilities.INTERNAL,
            DescriptorVisibilities.PRIVATE,
            DescriptorVisibilities.PROTECTED,
            DescriptorVisibilities.PUBLIC
        )
    )

    override fun updateSignatureAlarmFired() {
        super.updateSignatureAlarmFired()
        validateButtons()
    }

    override fun createSignaturePreviewComponent(): KotlinSignatureComponent = KotlinSignatureComponent(calculateSignature(), project)

    override fun validateAndCommitData(): String? {
        if (myMethod.canChangeReturnType() == MethodDescriptor.ReadWriteOption.ReadWrite &&
            myReturnTypeCodeFragment.getTypeInfo(isCovariant = true, forPreview = false).type == null &&
            !showWarningMessage(
                myProject,
                KotlinBundle.message("message.text.return.type.cannot.be.resolved", myReturnTypeCodeFragment?.text.toString()),
            )
        ) return EXIT_SILENTLY

        for (item in parametersTableModel.items) {
            if (item.typeCodeFragment.getTypeInfo(isCovariant = true, forPreview = false).type == null && !showWarningMessage(
                    myProject,
                    KotlinBundle.message(
                        "message.type.for.cannot.be.resolved",
                        item.typeCodeFragment.text,
                        if (item.parameter != parametersTableModel.receiver)
                            KotlinBundle.message("text.parameter.0", item.parameter.name)
                        else
                            KotlinBundle.message("text.receiver"),
                    ),
                )
            ) return EXIT_SILENTLY
        }

        return null
    }

    override fun getMethodName(): String = super.getMethodName().quoteIfNeeded()

    override fun canRun() {
        if (myNamePanel.isVisible && myMethod.canChangeName() && !methodName.isIdentifier()) {
            throw ConfigurationException(KotlinBundle.message("function.name.is.invalid"))
        }

        if (myMethod.canChangeReturnType() === MethodDescriptor.ReadWriteOption.ReadWrite) {
            (myReturnTypeCodeFragment as? KtTypeCodeFragment)?.validateElement(KotlinBundle.message("return.type.is.invalid"))
        }

        for (item in parametersTableModel.items) {
            val parameterName = item.parameter.name.quoteIfNeeded()

            if (item.parameter != parametersTableModel.receiver && !parameterName.isIdentifier()) {
                throw ConfigurationException(KotlinBundle.message("parameter.name.is.invalid", parameterName))
            }

            (item.typeCodeFragment as? KtTypeCodeFragment)?.validateElement(
                KotlinBundle.message(
                    "parameter.type.is.invalid",
                    item.typeCodeFragment.text
                )
            )
        }
    }

    override fun createRefactoringProcessor(): BaseRefactoringProcessor {
        val changeInfo = evaluateChangeInfo(
            parametersTableModel,
            myReturnTypeCodeFragment,
            getMethodDescriptor(),
            visibility,
            methodName,
            myDefaultValueContext,
            false
        )

        changeInfo.primaryPropagationTargets = myMethodsToPropagateParameters ?: emptyList()
        changeInfo.checkUsedParameters = true
        return KotlinChangeSignatureProcessor(myProject, changeInfo, commandName ?: title)
    }

    private fun getMethodDescriptor(): KotlinMethodDescriptor = myMethod

    override fun getSelectedIdx(): Int = myMethod.parameters
        .withIndex()
        .firstOrNull { it.value.isNewParameter }
        ?.index
        ?: editor?.let { editor ->
            myDefaultValueContext.containingFile
                .findElementForRename<KtParameter>(editor.caretModel.offset)
                ?.parameterIndex()
                ?.takeUnless { offset -> offset == -1 }
        }
        ?: super.getSelectedIdx()

    companion object {
        private fun ParameterTableModelItemBase<KotlinParameterInfo>.isReceiverIn(model: KotlinCallableParameterTableModel): Boolean =
            parameter == model.receiver

        /**
         * @return OK -> true, Cancel -> false
         */
        fun showWarningMessage(project: Project, message: @NlsContexts.DialogMessage String): Boolean =
            MessageDialogBuilder.okCancel(RefactoringBundle.message("changeSignature.refactoring.name"), message)
                .asWarning()
                .ask(project)

        private fun createParametersInfoModel(
            descriptor: KotlinMethodDescriptor,
            defaultValueContext: PsiElement
        ): KotlinCallableParameterTableModel = when (descriptor.kind) {
            Kind.FUNCTION -> KotlinFunctionParameterTableModel(descriptor, defaultValueContext)
            Kind.PRIMARY_CONSTRUCTOR -> KotlinPrimaryConstructorParameterTableModel(descriptor, defaultValueContext)
            Kind.SECONDARY_CONSTRUCTOR -> KotlinSecondaryConstructorParameterTableModel(descriptor, defaultValueContext)
        }

        private fun createReturnTypeCodeFragment(project: Project, method: KotlinMethodDescriptor): KtTypeCodeFragment {
            return KtPsiFactory(project).createTypeCodeFragment(
                method.returnTypeInfo.render(),
                KotlinCallableParameterTableModel.getTypeCodeFragmentContext(method.baseDeclaration)
            )
        }

        fun createRefactoringProcessorForSilentChangeSignature(
            project: Project,
            @NlsContexts.Command commandName: String,
            method: KotlinMethodDescriptor,
            defaultValueContext: PsiElement
        ): BaseRefactoringProcessor {
            val parameterTableModel = createParametersInfoModel(method, defaultValueContext)
            parameterTableModel.setParameterInfos(method.parameters)
            val changeInfo = evaluateChangeInfo(
                parameterTableModel,
                createReturnTypeCodeFragment(project, method),
                method,
                method.visibility,
                method.name,
                defaultValueContext,
                false
            )

            return KotlinChangeSignatureProcessor(project, changeInfo, commandName)
        }

        fun PsiCodeFragment?.getTypeInfo(isCovariant: Boolean, forPreview: Boolean): KotlinTypeInfo {
            if (this !is KtTypeCodeFragment) return KotlinTypeInfo(isCovariant)

            val typeRef = getContentElement()
            val typeRefText = typeRef?.text ?: return KotlinTypeInfo(isCovariant)
            val type = typeRef.analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, typeRef]?.takeUnless { it.isError }
            return KotlinTypeInfo(
                isCovariant,
                type,
                typeRefText.takeIf { forPreview || type == null },
            )
        }

        private fun evaluateChangeInfo(
            parametersModel: KotlinCallableParameterTableModel,
            returnTypeCodeFragment: PsiCodeFragment?,
            methodDescriptor: KotlinMethodDescriptor,
            visibility: DescriptorVisibility?,
            methodName: String,
            defaultValueContext: PsiElement,
            forPreview: Boolean
        ): KotlinChangeInfo {
            val parameters = parametersModel.items.map { parameter ->
                val parameterInfo = parameter.parameter
                if (!forPreview && parameter.isReceiverIn(parametersModel)) {
                    parameterInfo.defaultValueAsDefaultParameter = false
                }

                val kotlinTypeInfo = parameter.typeCodeFragment.getTypeInfo(false, forPreview)
                val newKotlinType = kotlinTypeInfo.type
                val oldKotlinType = parameterInfo.currentTypeInfo.type
                parameterInfo.currentTypeInfo = kotlinTypeInfo

                val codeFragment = parameter.defaultValueCodeFragment as KtExpressionCodeFragment
                if (newKotlinType != oldKotlinType && codeFragment.getContentElement() != null) {
                    codeFragment.putUserData(CodeFragmentAnalyzer.EXPECTED_TYPE_KEY, kotlinTypeInfo.type)
                    DaemonCodeAnalyzer.getInstance(codeFragment.project).restart(codeFragment)
                }

                if (!forPreview) AddFullQualifierIntention.addQualifiersRecursively(codeFragment)

                val oldDefaultValue = parameterInfo.defaultValueForCall
                if (codeFragment.text != (if (oldDefaultValue != null) oldDefaultValue.text else "")) {
                    parameterInfo.defaultValueForCall = codeFragment.getContentElement()
                }

                parameterInfo
            }

            val parametersWithReceiverInFirstPosition = if (parametersModel.receiver != null)
                parameters.sortedByDescending { it == parametersModel.receiver }
            else
                parameters

            return KotlinChangeInfo(
                methodDescriptor.original,
                methodName,
                returnTypeCodeFragment.getTypeInfo(true, forPreview),
                visibility ?: DescriptorVisibilities.DEFAULT_VISIBILITY,
                parametersWithReceiverInFirstPosition,
                parametersModel.receiver,
                defaultValueContext
            )
        }
    }
}
