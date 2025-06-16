// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.java.beans.PropertyKind.*
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmValue
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JvmPsiConversionHelper
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTypes
import com.intellij.psi.presentation.java.ClassPresentationUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.getPropertyNameByAccessorName
import org.jetbrains.plugins.groovy.lang.psi.util.getAccessorName
import org.jetbrains.plugins.groovy.lang.psi.util.getPropertyNameAndKind

internal class CreatePropertyAction(
  target: GrTypeDefinition,
  override val request: CreateMethodRequest,
  private val readOnly: Boolean
) : CreateMemberAction(target, request), JvmGroupIntentionAction {

  private val project = target.project
  private val propertyInfo get() = requireNotNull(getPropertyNameAndKind(request.methodName))

  override fun getRenderData() = JvmActionGroup.RenderData { propertyInfo.first }

  override fun getFamilyName(): String = message("create.property.from.usage.family")

  override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo {
    return CreateFieldAction(target, PropertyRequest(), false).generatePreview(project, editor, psiFile)
  }

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
    if (!super.isAvailable(project, editor, psiFile)) return false
    val (propertyName, propertyKind) = getPropertyNameAndKind(request.methodName) ?: return false

    if (propertyKind == SETTER && readOnly) return false

    val parameters = request.expectedParameters
    when (propertyKind) {
      GETTER, BOOLEAN_GETTER -> if (parameters.isNotEmpty()) return false
      SETTER -> if (parameters.size != 1) return false
    }

    val counterAccessorName = counterPart(propertyKind).getAccessorName(propertyName)
    return target.findMethodsByName(counterAccessorName, false).isEmpty()
  }

  private fun counterPart(propertyKind: PropertyKind): PropertyKind {
    return when (propertyKind) {
      GETTER, BOOLEAN_GETTER -> SETTER
      SETTER -> {
        val expectedType = request.expectedParameters.single().expectedTypes.singleOrNull()
        if (expectedType != null && PsiTypes.booleanType() == JvmPsiConversionHelper.getInstance(project).convertType(expectedType.theType)) {
          BOOLEAN_GETTER
        }
        else {
          GETTER
        }
      }
    }
  }

  override fun getText(): String {
    val propertyName = getPropertyNameByAccessorName(request.methodName)
    val className = ClassPresentationUtil.getNameForClass(target, false)
    return if (readOnly) {
      message("create.read.only.property.from.usage.full.text", propertyName, className)
    }
    else {
      message("create.property.from.usage.full.text", propertyName, className)
    }
  }

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    CreateFieldAction(target, PropertyRequest(), false).apply {
      if (isAvailable(project, editor, psiFile)) invoke(project, editor, psiFile)
    }
  }

  override fun getActionGroup(): JvmActionGroup = if (readOnly) CreateReadOnlyPropertyActionGroup else CreatePropertyActionGroup

  inner class PropertyRequest : CreateFieldRequest {
    override fun isValid() = true

    override fun getAnnotations(): Collection<AnnotationRequest> = emptyList()

    override fun getModifiers() = if (readOnly) listOf(JvmModifier.FINAL) else emptyList()

    override fun getFieldName() = propertyInfo.first

    override fun getFieldType() = request.createPropertyTypeConstraints(propertyInfo.second)

    override fun getTargetSubstitutor() = request.targetSubstitutor

    override fun isConstant(): Boolean = false

    override fun getInitializer(): JvmValue? = null

    override fun isStartTemplate(): Boolean = request.isStartTemplate
  }
}