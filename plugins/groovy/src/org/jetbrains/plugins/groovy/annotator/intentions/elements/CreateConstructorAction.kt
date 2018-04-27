// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateConstructorRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils.createTemplateForMethod
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

internal class CreateConstructorAction(
  targetClass: GrTypeDefinition,
  override val request: CreateConstructorRequest
) : CreateMemberAction(targetClass, request) {


  override fun getFamilyName(): String = message("create.constructor.family")

  override fun getText(): String = message("create.constructor.from.new.text")

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    ConstructorMethodRenderer(project, target, request).execute()
  }
}

private class ConstructorMethodRenderer(
  val project: Project,
  val targetClass: GrTypeDefinition,
  val request: CreateConstructorRequest
) {

  val factory = GroovyPsiElementFactory.getInstance(project)

  fun execute() {
    var constructor = renderConstructor()
    constructor = insertConstructor(constructor)
    constructor = forcePsiPostprocessAndRestoreElement(constructor) ?: return
    setupTemplate(constructor)
  }

  private fun setupTemplate(method: GrMethod) {
    val parameters = request.expectedParameters
    val typeExpressions = setupParameters(method, parameters).toTypedArray()
    val nameExpressions = setupNameExpressions(parameters, project).toTypedArray()
    createTemplateForMethod(typeExpressions, nameExpressions, method, targetClass, null, true, null)
  }

  private fun renderConstructor(): GrMethod {
    val constructor = factory.createConstructor()

    val modifiersToRender = request.modifiers.toMutableList()

    modifiersToRender -= JvmModifier.PUBLIC //public by default

    for (modifier in modifiersToRender) {
      constructor.modifierList.setModifierProperty(modifier.toPsiModifier(), true)
    }

    for (annotation in request.annotations) {
      constructor.modifierList.addAnnotation(annotation.qualifiedName)
    }

    return constructor
  }

  private fun insertConstructor(method: GrMethod): GrMethod {
    return targetClass.add(method) as GrMethod
  }
}