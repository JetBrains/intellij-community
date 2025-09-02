// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.codeInsight.CodeInsightUtil.positionCursor
import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.startTemplate
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.lang.jvm.actions.CreateEnumConstantActionGroup
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.ExpectedTypes
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant

internal class CreateEnumConstantAction(
  target: GrTypeDefinition,
  override val request: CreateFieldRequest
) : CreateFieldActionBase(target, request) {

  override fun getActionGroup(): JvmActionGroup = CreateEnumConstantActionGroup

  override fun getText(): String = GroovyBundle.message("intention.name.create.enum.constant.0", request.fieldName)

  override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo {
    val (_, _, constant) = getEnumConstantTriple(project)
    val className = myTargetPointer.element?.name
    return IntentionPreviewInfo.CustomDiff(GroovyFileType.GROOVY_FILE_TYPE, className, "", constant.text)
  }

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    val (targetClass, parameters, enumConstant) = getEnumConstantTriple(project)

    var added = targetClass.add(enumConstant) as GrEnumConstant
    if (parameters.isEmpty()) return

    // start template
    val builder = TemplateBuilderImpl(added)
    val argumentList = added.argumentList!!
    for (expression in argumentList.expressionArguments) {
      builder.replaceElement(expression, EmptyExpression())
    }
    added = forcePsiPostprocessAndRestoreElement(added) ?: return
    val template = builder.buildTemplate()

    val newEditor = positionCursor(project, targetClass.containingFile, added) ?: return
    val range = added.textRange
    newEditor.document.deleteString(range.startOffset, range.endOffset)
    startTemplate(newEditor, template, project)
  }

  private fun getEnumConstantTriple(project: Project): Triple<GrTypeDefinition, String, GrEnumConstant> {
    val name = request.fieldName
    val targetClass = target
    val parameters = renderParameters(targetClass)
    val text = if (parameters.isEmpty()) name else "$name($parameters)"
    val enumConstant = renderConstant(project, text)
    return Triple(targetClass, parameters, enumConstant)
  }
}

internal fun renderConstant(project: Project, text: String): GrEnumConstant {
  val elementFactory = GroovyPsiElementFactory.getInstance(project)
  return elementFactory.createEnumConstantFromText(text)
}

internal fun renderParameters(targetClass: GrTypeDefinition): String {
  val constructor = targetClass.constructors.firstOrNull() ?: return ""
  val parameters = constructor.parameterList.parameters

  return parameters.joinToString(",") { it.name }
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
