// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.formConversion

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.uiDesigner.PsiPropertiesProvider
import com.intellij.uiDesigner.binding.FormClassIndex
import com.intellij.uiDesigner.compiler.Utils.getRootContainer
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.lw.*

/**
 * @author yole
 */
class ConvertFormToDslAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getRequiredData(CommonDataKeys.EDITOR)
    val psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE) as PsiJavaFile
    val project = psiFile.project
    val module = ModuleUtil.findModuleForPsiElement(psiFile) ?: return
    val element = psiFile.findElementAt(editor.caretModel.offset)
    val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: run {
      HintManager.getInstance().showErrorHint(editor, "Please put a caret inside a Java class bound to a form")
      return
    }
    val qName = psiClass.qualifiedName ?: return
    val formFile = FormClassIndex.findFormsBoundToClass(project, qName).singleOrNull() ?: run {
      HintManager.getInstance().showErrorHint(editor, "Can't find a form bound to ${qName}")
      return
    }

    val rootContainer = getRootContainer(formFile.text, PsiPropertiesProvider(module))
    val form = convertRootContainer(module, rootContainer)

    val formText = buildString {
      append("val panel = panel {\n")
      form.root.render(this)
      append("}\n")
    }
    val uiName = "${psiClass.name}Ui"
    val ktFileType = FileTypeRegistry.getInstance().getFileTypeByExtension("kt")
    val ktFileText = buildString {
      if (psiFile.packageName.isNotEmpty()) {
        append("package ${psiFile.packageName}\n\n")
      }
      append("import com.intellij.ui.layout.*\n")
      for (usedImport in form.imports) {
        append("import $usedImport\n")
      }
      append("\n")
      append("class $uiName {")

      for (binding in form.bindings) {
        append("lateinit var ${binding.name}: ${binding.type.substringAfterLast('.')}\n")
      }

      append(formText)
      append("}")
    }
    val ktFile = PsiFileFactory.getInstance(project).createFileFromText("$uiName.kt", ktFileType, ktFileText)
    WriteCommandAction.runWriteCommandAction(project) {
      val ktFileReal = psiFile.containingDirectory.add(ktFile) as PsiFile
      CodeStyleManager.getInstance(project).reformat(ktFileReal)
      ktFileReal.navigate(true)
    }
  }

  private fun convertRootContainer(module: Module, rootContainer: LwRootContainer): UiForm {
    val call = convertContainer(rootContainer)
    for (buttonGroup in rootContainer.buttonGroups) {
      call.checkConvertButtonGroup(buttonGroup.componentIds)
    }
    return UiForm(module, call)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.getData(CommonDataKeys.PSI_FILE) is PsiJavaFile &&
                               e.getData(CommonDataKeys.EDITOR) != null
  }
}

class FormCall(
  val callee: String,
  val args: MutableList<String> = mutableListOf(),
  val contents: MutableList<FormCall> = mutableListOf(),
  var origin: IComponent? = null,
  val binding: String? = null,
  val bindingType: String? = null
) {
  constructor(callee: String, vararg args: String): this(callee) {
    this.args.addAll(args.toList())
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
    if (binding != null) {
      builder.append(".also { $binding = it }")
    }
    builder.append("\n")
  }
}

data class Binding(val name: String, val type: String)

class UiForm(module: Module, val root: FormCall) {
  private val _imports = sortedSetOf<String>()
  private val _bindings = mutableListOf<Binding>()

  val imports: Collection<String> get() { return _imports }
  val bindings: Collection<Binding> get() { return _bindings }

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
    formCall.bindingType?.let { bindingType ->
      _imports.add(bindingType)
      formCall.binding?.let { bindingName -> _bindings.add(Binding(bindingName, bindingType))}
    }

    for (content in formCall.contents) {
      collectUsedImportsAndBindings(module, content)
    }
  }
}


private fun convertContainer(container: LwContainer): FormCall {
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

fun FormCall.appendGridRow(container: LwContainer, layoutManager: GridLayoutManager, rowIndex: Int) {
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
  if (component is LwContainer) {
    return convertContainer(component)
  }
  else {
    return convertComponent(component).also { it.origin = component }
  }
}

private fun convertComponent(component: IComponent): FormCall {
  return when (component.componentClassName) {
    "javax.swing.JCheckBox" ->
      FormCall("checkBox", convertComponentText(component))

    "javax.swing.JTextField" ->
      FormCall("textField", "{ \"\" }", "{}")

    "javax.swing.JRadioButton" ->
      FormCall("radioButton", convertComponentText(component))

    "javax.swing.JButton" ->
      FormCall("button", convertComponentText(component), "actionListener = {}")

    else -> {
      FormCall("${component.componentClassName.substringAfterLast('.')}()",
               binding = component.binding,
               bindingType = component.componentClassName)
    }
  }
}

private fun convertComponentText(component: IComponent) =
  convertStringDescriptor(component.getPropertyValue("text") as StringDescriptor)

private fun convertStringDescriptor(text: StringDescriptor): String {
  text.value?.let {
    return "\"${StringUtil.escapeQuotes(it)}\""
  }
  return "${text.bundleName.substringAfterLast('/')}.message(\"${text.key}\")"
}

private fun IComponent.getPropertyValue(name: String): Any? {
  val prop = modifiedProperties.find { it.name == name } ?: return null
  return prop.getPropertyValue(this)
}

fun FormCall.checkConvertButtonGroup(ids: Array<String>) {
  if (contents.any { it.isRadioButtonRow(ids) }) {
    val buttonGroupNode = FormCall("buttonGroup")
    buttonGroupNode.contents.addAll(contents)
    contents.clear()
    contents.add(buttonGroupNode)

    return
  }

  for (content in contents) {
    content.checkConvertButtonGroup(ids)
  }
}

private fun FormCall.isRadioButtonRow(ids: Array<String>): Boolean {
  return callee == "row" && contents.singleOrNull()?.origin?.id?.let { it in ids } == true
}
