// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.formConversion

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.uiDesigner.PsiPropertiesProvider
import com.intellij.uiDesigner.binding.FormClassIndex
import com.intellij.uiDesigner.compiler.Utils.getRootContainer
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.lw.*
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UastVisibility
import org.jetbrains.uast.toUElement
import java.util.*


class ConvertFormToDslAction /* todo IDEA-282478 : AnAction() */ {
  fun actionPerformed(e: AnActionEvent) {
    val editor = e.getRequiredData(CommonDataKeys.EDITOR)
    val psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE) as PsiJavaFile
    val project = psiFile.project
    val element = psiFile.findElementAt(editor.caretModel.offset)
    val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: run {
      HintManager.getInstance().showErrorHint(editor, DevKitBundle.message("convert.form.hint.caret.not.in.form.bound.class"))
      return
    }
    val qName = psiClass.qualifiedName ?: return
    val formFile = FormClassIndex.findFormsBoundToClass(project, qName).singleOrNull() ?: run {
      HintManager.getInstance().showErrorHint(editor, DevKitBundle.message("convert.form.hint.class.not.bound.to.form", qName))
      return
    }

    convertFormToUiDsl(psiClass, formFile)
  }

  fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.getData(CommonDataKeys.PSI_FILE) is PsiJavaFile &&
                               e.getData(CommonDataKeys.EDITOR) != null
  }
}

@Suppress("HardCodedStringLiteral")
fun convertFormToUiDsl(boundClass: PsiClass, formFile: PsiFile) {
  val project = boundClass.project
  val psiFile = boundClass.containingFile as PsiJavaFile
  val module = ModuleUtil.findModuleForPsiElement(psiFile) ?: return

  val dialog = ConvertFormDialog(project, "${boundClass.name}Ui")
  if (!dialog.showAndGet()) return

  val boundInstanceUClass = findBoundInstanceUClass(project, dialog.boundInstanceType)

  val rootContainer = getRootContainer(formFile.text, PsiPropertiesProvider(module))
  val form = convertRootContainer(module, rootContainer, boundInstanceUClass)

  val imports = LinkedHashSet(form.imports)
  boundInstanceUClass?.qualifiedName?.let {
    imports.add(it)
  }
  if (dialog.generateDescriptors) {
    imports.add("com.intellij.application.options.editor.*")
  }
  if (dialog.baseClass == ConvertFormDialog.FormBaseClass.Configurable) {
    imports.add("com.intellij.openapi.options.BoundConfigurable")
    imports.add("com.intellij.openapi.ui.DialogPanel")
  }

  val optionDescriptors = if (dialog.generateDescriptors) {
    buildString {
      generateOptionDescriptors(form.root, this)
    }
  }
  else {
    ""
  }
  val uiName = dialog.className
  val ktFileType = FileTypeRegistry.getInstance().getFileTypeByExtension("kt")
  val ktFileText = buildString {
    if (psiFile.packageName.isNotEmpty()) {
      append("package ${psiFile.packageName}\n\n")
    }
    append("import com.intellij.ui.layout.*\n")
    for (usedImport in imports) {
      append("import $usedImport\n")
    }
    append("\n")

    if (optionDescriptors.isNotEmpty()) {
      append("private val model = ${dialog.boundInstanceExpression}")
      append(optionDescriptors)
      append("\n")
    }

    append("class $uiName")
    if (dialog.baseClass == ConvertFormDialog.FormBaseClass.Configurable) {
      append(" : BoundConfigurable(TODO(), TODO())")
    }
    else if (boundInstanceUClass != null) {
      append("(val model: ${dialog.boundInstanceType.substringAfterLast('.')})")
    }
    append(" {")

    for (binding in form.componentBindings) {
      val typeParameters = buildTypeParametersString(module, binding.type)
      append("lateinit var ${binding.name}: ${binding.type.substringAfterLast('.')}$typeParameters\n")
    }

    if (dialog.baseClass == ConvertFormDialog.FormBaseClass.Configurable) {
      append("override fun createPanel(): DialogPanel {\n")
      if (dialog.boundInstanceExpression.isNotEmpty()) {
        append("val model = ${dialog.boundInstanceExpression}\n")
      }
      else if (boundInstanceUClass != null) {
        append("val model: ${dialog.boundInstanceType.substringAfterLast('.')} = TODO()\n")
      }

      append("return panel {\n")
      form.root.render(this)
      append("}\n")
    }
    else {
      append("val panel = panel {\n")
      form.root.render(this)
      append("}\n")
    }

    append("}")
  }
  val ktFile = PsiFileFactory.getInstance(project).createFileFromText("$uiName.kt", ktFileType, ktFileText)
  WriteCommandAction.runWriteCommandAction(project) {
    val ktFileReal = psiFile.containingDirectory.add(ktFile) as PsiFile
    CodeStyleManager.getInstance(project).reformat(ktFileReal)
    ktFileReal.navigate(true)
  }
}

private fun findBoundInstanceUClass(project: Project, boundInstanceType: String): UClass? {
  val psiClass = JavaPsiFacade.getInstance(project).findClass(boundInstanceType, ProjectScope.getAllScope(project))
  if (psiClass == null) return null
  return psiClass.navigationElement.toUElement(UClass::class.java)
}

private fun convertRootContainer(module: Module,
                                 rootContainer: LwRootContainer,
                                 boundInstanceUClass: UClass?): UiForm {
  val call = FormToDslConverter(module, boundInstanceUClass).convertContainer(rootContainer)
  for (buttonGroup in rootContainer.buttonGroups) {
    call.checkConvertButtonGroup(buttonGroup.componentIds)
  }
  return UiForm(module, call)
}

@Suppress("HardCodedStringLiteral")
private fun generateOptionDescriptors(call: FormCall, optionDescriptors: StringBuilder) {
  val propertyName = call.binding
  val size = call.args.size
  if (call.callee == "checkBox" && propertyName != null && size >= 1) {
    optionDescriptors.append("val $propertyName = CheckboxDescriptor(${call.args[0]}, ")

    val propertyBindingArg =
      when {
        size == 1 -> "TODO()"
        size == 2 -> "${call.args[1]}.toBinding()"
        else -> "PropertyBinding(${call.args[1]}, ${call.args[2]})"
      }

    optionDescriptors.append(propertyBindingArg).append(")\n")

    call.args.clear()
    call.args.add(propertyName)
  }

  for (content in call.contents) {
    generateOptionDescriptors(content, optionDescriptors)
  }
}

@Suppress("HardCodedStringLiteral")
class FormCall(
  val callee: String,
  val args: MutableList<String> = mutableListOf(),
  val contents: MutableList<FormCall> = mutableListOf(),
  var origin: IComponent? = null,
  var binding: String? = null,
  var bindingType: String? = null,
  var generateProperty: Boolean = false
) {
  constructor(callee: String, vararg args: String): this(callee) {
    this.args.addAll(args.toList())
  }

  fun addArgIfPresent(arg: Array<String>?): FormCall {
    if (arg != null) {
      Collections.addAll(args, *arg)
    }
    return this
  }

  fun addArgOrDefault(arg: Array<String>?, vararg default: String): FormCall {
    if (arg != null) {
      Collections.addAll(args, *arg)
    }
    else {
      Collections.addAll(args, *default)
    }
    return this
  }

  fun render(builder: StringBuilder) {
    if (callee == "row" && args.isEmpty() && contents.all { it.callee == "row" }) {
      for (content in contents) {
        content.render(builder)
      }
      return
    }

    builder.append(callee)
    if (args.isNotEmpty()) {
      builder.append(args.joinToString(prefix = "(", postfix = ")", separator = ", "))
    }
    if (contents.isNotEmpty()) {
      builder.append("{\n")
      for (content in contents) {
        content.render(builder)
      }
      builder.append("}")
    }
    if (generateProperty) {
      builder.append(".also { $binding = it }")
    }
    builder.append("\n")
  }
}

data class ComponentBinding(val name: String, val type: String)

@Suppress("HardCodedStringLiteral")
class UiForm(module: Module, val root: FormCall) {
  private val _imports = sortedSetOf<String>()
  private val _componentBindings = mutableListOf<ComponentBinding>()

  val imports: Collection<String> get() { return _imports }
  val componentBindings: Collection<ComponentBinding> get() { return _componentBindings }

  init {
    collectUsedImportsAndBindings(module, root)
  }

  private fun collectUsedImportsAndBindings(module: Module, formCall: FormCall) {
    for (arg in formCall.args) {
      val callee = arg.substringBefore('.', "")
      if (callee.endsWith("Bundle")) {
        val shortNamesCache = PsiShortNamesCache.getInstance(module.project)
        val classesByName = shortNamesCache.getClassesByName(callee, module.moduleContentWithDependenciesScope)
        if (classesByName.isNotEmpty()) {
          val qualifiedName = classesByName[0].qualifiedName
          if (qualifiedName != null) {
            _imports.add(qualifiedName)
          }
        }
      }
    }
    if (formCall.generateProperty) {
      formCall.bindingType?.let { bindingType ->
        _imports.add(bindingType)
        formCall.binding?.let { bindingName -> _componentBindings.add(ComponentBinding(bindingName, bindingType))}
      }
    }

    for (content in formCall.contents) {
      collectUsedImportsAndBindings(module, content)
    }
  }
}

internal class PropertyBinding(val type: PsiType?, val bindingCallParameters: Array<String>)

@Suppress("HardCodedStringLiteral")
class FormToDslConverter(private val module: Module, private val boundInstanceUClass: UClass?) {
  fun convertContainer(container: LwContainer): FormCall {
    val row: FormCall

    val borderTitle = container.borderTitle
    if (borderTitle != null) {
      row = FormCall("titledRow", origin = container)
      row.args.add(convertStringDescriptor(borderTitle))
    }
    else {
      row = FormCall("row", origin = container)
    }

    val layoutManager = container.layout
    if (layoutManager is GridLayoutManager) {
      for (rowIndex in 0 until layoutManager.rowCount) {
        row.appendGridRow(container, layoutManager, rowIndex)
      }
    }
    else {
      for (index in 0 until container.componentCount) {
        row.contents.add(convertComponentOrContainer(container.getComponent(index)))
      }
    }
    return row
  }

  private fun FormCall.appendGridRow(container: LwContainer, layoutManager: GridLayoutManager, rowIndex: Int) {
    val allComponents = container.collectComponentsInRow(rowIndex, layoutManager.columnCount)
    val components = allComponents.filter { it !is LwHSpacer && it !is LwVSpacer }
    if (components.isEmpty()) return

    val row = FormCall("row", origin = container)
    contents.add(row)
    if (components.first().componentClassName == "javax.swing.JLabel") {
      row.args.add(convertComponentText(components.first()))
      for (component in components.drop(1)) {
        row.contents.add(convertComponentOrContainer(component))
      }
    }
    else {
      for (component in components) {
        row.contents.add(convertComponentOrContainer((component)))
      }
    }
  }

  private fun LwContainer.collectComponentsInRow(row: Int, columnCount: Int): List<IComponent> {
    val result = arrayOfNulls<IComponent>(columnCount)
    for (i in 0 until componentCount) {
      val component = getComponent(i)

      val constraints = component.constraints
      if (constraints.row == row) {
        result[constraints.column] = component
      }
    }
    return result.toList().filterNotNull()
  }

  private fun convertComponentOrContainer(component: IComponent): FormCall {
    val result = if (component is LwContainer) {
      return convertContainer(component)
    }
    else {
      convertComponent(component)
    }
    result.origin = component
    result.binding = component.binding
    result.bindingType = component.componentClassName
    return result
  }

  private fun convertComponent(component: IComponent): FormCall {
    val propertyBinding = convertBinding(component.binding)
    return when (component.componentClassName) {
      "javax.swing.JCheckBox",
      "com.intellij.ui.components.JBCheckBox" ->
        FormCall("checkBox", convertComponentText(component))
          .addArgIfPresent(propertyBinding?.bindingCallParameters)

      "javax.swing.JTextField" -> {
        val methodName = if (propertyBinding?.type?.canonicalText == "int") "intTextField" else "textField"
        FormCall(methodName)
          .addArgOrDefault(propertyBinding?.bindingCallParameters, "{ \"\" }", "{}")
      }

      "com.intellij.openapi.ui.TextFieldWithBrowseButton" -> {
        FormCall("textFieldWithBrowseButton")
          .addArgOrDefault(propertyBinding?.bindingCallParameters, "{ \"\" }", "{}")
      }

      "javax.swing.JRadioButton",
      "com.intellij.ui.components.JBRadioButton"->
        FormCall("radioButton", convertComponentText(component))

      "javax.swing.JButton" ->
        FormCall("button", convertComponentText(component), "actionListener = { TODO() }")

      "javax.swing.JLabel",
      "com.intellij.ui.components.JBLabel" ->
        FormCall("label", convertComponentText(component))

      "javax.swing.JComboBox",
      "com.intellij.openapi.ui.ComboBox" ->
        FormCall("comboBox", "TODO()")

      else -> {
        val typeParameters = buildTypeParametersString(module, component.componentClassName)

        val classShortName = component.componentClassName.substringAfterLast('.')
        FormCall("$classShortName$typeParameters()", generateProperty = true)
      }
    }
  }

  private fun convertBinding(binding: String?): PropertyBinding? {
    if (binding == null || boundInstanceUClass == null) return null
    val field = boundInstanceUClass.fields.find { it.matchesBinding(binding) }
    if (field != null && !field.isStatic && field.visibility != UastVisibility.PRIVATE) {
      return PropertyBinding(field.type, arrayOf("model::${field.name}"))
    }

    val getter = boundInstanceUClass.methods.find {
      !it.name.startsWith("set") && it.matchesBinding(binding)
    }
    val setter = boundInstanceUClass.methods.find {
      it.name.startsWith("set") && it.matchesBinding(binding)
    }
    if (getter != null && setter != null) {
      return PropertyBinding(getter.returnType, arrayOf("model::${getter.name}", "model::${setter.name}"))
    }

    return null
  }

  private fun PsiNamedElement.matchesBinding(binding: String): Boolean {
    val bindingWords = NameUtil.nameToWordsLowerCase(binding.removePrefix("my"))
    val elementWords = NameUtil.nameToWordsLowerCase(name?.removePrefix("my") ?: "")
    if (bindingWords.size == 1 && elementWords.size == 1) {
      return bindingWords[0] == elementWords[0]
    }
    return bindingWords.count { it in elementWords } > 1
  }

  private fun convertComponentText(component: IComponent): String {
    val propertyValue = component.getPropertyValue("text") ?: return ""
    return convertStringDescriptor(propertyValue as StringDescriptor)
  }

  private fun convertStringDescriptor(text: StringDescriptor): String {
    text.value?.let {
      return "\"${StringUtil.escapeQuotes(it)}\""
    }
    return "${text.bundleName.substringAfterLast('/')}.message(\"${text.key}\")"
  }
}

  @Suppress("HardCodedStringLiteral")
private fun buildTypeParametersString(module: Module, className: String): String {
  val javaPsiFacade = JavaPsiFacade.getInstance(module.project)
  val componentClass = javaPsiFacade.findClass(className, module.moduleWithDependenciesScope)
  return if (componentClass != null && componentClass.typeParameters.isNotEmpty()) {
    Array(componentClass.typeParameters.size) { "Any" }.joinToString(prefix = "<", postfix = ">", separator = ", ")
  }
  else
    ""
}

private fun IComponent.getPropertyValue(name: String): Any? {
  val prop = modifiedProperties.find { it.name == name } ?: return null
  return prop.getPropertyValue(this)
}

fun FormCall.checkConvertButtonGroup(ids: Array<String>) {
  if (contents.any { it.isRadioButtonRow(ids) }) {
    val firstIndex = contents.indexOfFirst { it.isRadioButtonRow(ids) }
    val lastIndex = contents.indexOfLast { it.isRadioButtonRow(ids) }

    val buttonGroupNode = FormCall("buttonGroup")
    val buttonGroupControls = contents.subList(firstIndex, lastIndex + 1)
    buttonGroupNode.contents.addAll(buttonGroupControls)
    contents.removeAll(buttonGroupControls)
    contents.add(firstIndex, buttonGroupNode)

    return
  }

  for (content in contents) {
    content.checkConvertButtonGroup(ids)
  }
}

@Suppress("HardCodedStringLiteral")
private fun FormCall.isRadioButtonRow(ids: Array<String>): Boolean {
  return callee == "row" && contents.singleOrNull()?.origin?.id?.let { it in ids } == true
}
