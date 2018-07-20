// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.PsiType
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils.createTemplateForMethod
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

/**
 * @param abstract whether this action creates a method with explicit abstract modifier
 */
internal class CreateMethodAction(
  targetClass: GrTypeDefinition,
  override val request: CreateMethodRequest,
  private val abstract: Boolean
) : CreateMemberAction(targetClass, request), JvmGroupIntentionAction {

  override fun getActionGroup(): JvmActionGroup = if (abstract) CreateAbstractMethodActionGroup else CreateMethodActionGroup

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return super.isAvailable(project, editor, file) && PsiNameHelper.getInstance(project).isIdentifier(request.methodName)
  }

  override fun getRenderData() = JvmActionGroup.RenderData { request.methodName }

  override fun getFamilyName(): String = message("create.method.from.usage.family")

  override fun getText(): String {
    val what = request.methodName
    val where = getNameForClass(target, false)
    return if (abstract && !target.isInterface) {
      message("create.abstract.method.from.usage.full.text", what, where)
    }
    else {
      message("create.method.from.usage.full.text", what, where)
    }
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    MethodRenderer(project, abstract, target, request).execute()
  }
}

private class MethodRenderer(
  val project: Project,
  val abstract: Boolean,
  val targetClass: GrTypeDefinition,
  val request: CreateMethodRequest
) {

  fun execute() {
    var method = renderMethod()
    method = insertMethod(method)
    method = forcePsiPostprocessAndRestoreElement(method) ?: return
    setupTemplate(method)
  }

  private fun setupTemplate(method: GrMethod) {
    val parameters = request.expectedParameters
    val typeExpressions = setupParameters(method, parameters).toTypedArray()
    val nameExpressions = setupNameExpressions(parameters, project).toTypedArray()
    val returnExpression = setupTypeElement(method, createConstraints(project, request.returnType))
    createTemplateForMethod(typeExpressions, nameExpressions, method, targetClass, returnExpression, false, null)
  }

  private fun renderMethod(): GrMethod {
    val factory = GroovyPsiElementFactory.getInstance(project)
    val method = factory.createMethod(request.methodName, PsiType.VOID)

    val modifiersToRender = request.modifiers.toMutableList()
    if (targetClass.isInterface) {
      modifiersToRender -= (visibilityModifiers + JvmModifier.ABSTRACT)
    }
    else if (abstract) {
      if (modifiersToRender.remove(JvmModifier.PRIVATE)) {
        modifiersToRender += JvmModifier.PROTECTED
      }
      modifiersToRender += JvmModifier.ABSTRACT
    }
    modifiersToRender -= JvmModifier.PUBLIC //public by default

    val modifierList = method.modifierList
    for (modifier in modifiersToRender) {
      modifierList.setModifierProperty(modifier.toPsiModifier(), true)
    }

    modifierList.setModifierProperty(GrModifier.DEF, true)

    for (annotation in request.annotations) {
      modifierList.addAnnotation(annotation.qualifiedName)
    }

    if (abstract) method.body?.delete()

    return method
  }

  private fun insertMethod(method: GrMethod): GrMethod {
    return targetClass.add(method) as GrMethod
  }
}