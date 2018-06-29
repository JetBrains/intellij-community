// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.positionCursor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.startTemplate
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.lang.jvm.actions.CreateEnumConstantActionGroup
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.ExpectedTypes
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant

internal class CreateEnumConstantAction(
  target: GrTypeDefinition,
  override val request: CreateFieldRequest
) : CreateFieldActionBase(target, request) {

  override fun getActionGroup(): JvmActionGroup = CreateEnumConstantActionGroup

  override fun getText(): String = QuickFixBundle.message("create.enum.constant.from.usage.text", request.fieldName)

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val name = request.fieldName
    val targetClass = target
    val parameters = renderParameters(targetClass)
    val text = if (parameters.isEmpty()) name else "$name($parameters)"
    var enumConstant = renderConstant(project, text)

    enumConstant = targetClass.add(enumConstant) as GrEnumConstant
    if (parameters.isEmpty()) return

    // start template
    val builder = TemplateBuilderImpl(enumConstant)
    val argumentList = enumConstant.argumentList!!
    for (expression in argumentList.expressionArguments) {
      builder.replaceElement(expression, EmptyExpression())
    }
    enumConstant = forcePsiPostprocessAndRestoreElement(enumConstant)
    val template = builder.buildTemplate()

    val newEditor = positionCursor(project, targetClass.containingFile, enumConstant) ?: return
    val range = enumConstant.textRange
    newEditor.document.deleteString(range.startOffset, range.endOffset)
    startTemplate(newEditor, template, project)
  }
}

internal fun renderConstant(project: Project, text: String): GrEnumConstant {
  val elementFactory = GroovyPsiElementFactory.getInstance(project)
  return elementFactory.createEnumConstantFromText(text)
}

internal fun renderParameters(targetClass: GrTypeDefinition): String {
  val constructor = targetClass.constructors.firstOrNull() ?: return ""
  val parameters = constructor.parameterList.parameters

  return parameters.joinToString(",") { it.name ?: "" }
}

internal fun canCreateEnumConstant(targetClass: GrTypeDefinition, request: CreateFieldRequest): Boolean {
  if (!targetClass.isEnum) return false

  val lastConstant = targetClass.fields.filterIsInstance<GrEnumConstant>().lastOrNull()
  if (lastConstant != null && PsiTreeUtil.hasErrorElements(lastConstant)) return false

  return checkExpectedTypes(request.fieldType, targetClass, targetClass.project)
}

private fun checkExpectedTypes(types: ExpectedTypes, targetClass: GrTypeDefinition, project: Project): Boolean {
  val constraints = createConstraints(project, types)
  if (constraints.isEmpty()) return true
  val enumType = GroovyPsiElementFactory.getInstance(project).createType(targetClass)
  return constraints.any {
    it.satisfied(enumType, targetClass)
  }
}
