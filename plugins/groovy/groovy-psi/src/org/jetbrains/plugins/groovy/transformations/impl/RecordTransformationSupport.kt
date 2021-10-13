// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor
import org.jetbrains.plugins.groovy.extensions.impl.NamedArgumentDescriptorImpl
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrRecordDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.*
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.isRecordTransformationApplied
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext

class RecordTransformationSupport : AstTransformationSupport {
  override fun applyTransformation(context: TransformationContext) = with(context) {
    if (codeClass is GrRecordDefinition) {
      transformSyntacticRecord(codeClass as GrRecordDefinition)
    }
    if (!isRecordTransformationApplied(context)) {
      return
    }
    performClassTransformation()
    val currentFields = fields.toList()
    for (field in currentFields) {
      generateRecordProperty(field)
    }
  }


  private fun TransformationContext.transformSyntacticRecord(record: GrRecordDefinition) {
    for (formalParameter in record.parameters) {
      val field = GrLightField(record, formalParameter.name, formalParameter.type, formalParameter)
      field.modifierList.addModifier(GrModifierFlags.FINAL_MASK)
      addField(field)
    }
    val modifierList = record.modifierList
    if (modifierList != null) {
      fun doAddAnnotation(annoName : String, annoIdentifiers : Map<String, String>) {
        addAnnotation(GrLightAnnotation(modifierList, record, annoName, annoIdentifiers))
      }
      doAddAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_RECORD_BASE, emptyMap())
      doAddAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_IMMUTABLE_OPTIONS, emptyMap())
      doAddAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_PROPERTY_OPTIONS, mapOf("propertyHandler" to "ImmutablePropertyHandler"))
      doAddAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_KNOWN_IMMUTABLE, emptyMap())
      doAddAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_STC_POJO, emptyMap())
    }
    val formalParameters = record.parameters
    val constructor = GrLightMethodBuilder(record.manager, record.name).apply {
      isConstructor = true
      returnType = record.type()
      for (formalParameter in formalParameters) {
        addParameter(GrLightParameter(formalParameter.name, formalParameter.type, record))
      }
      navigationElement = parameterList
    }
    addMethod(constructor)
    val mapConstructor = GrLightMethodBuilder(record.manager, record.name).apply {
      isConstructor = true
      returnType = record.type()
      addParameter("args", CommonClassNames.JAVA_UTIL_MAP)
      namedParameters = formalParameters.associate { param -> param.name to object : NamedArgumentDescriptorImpl(NamedArgumentDescriptor.Priority.ALWAYS_ON_TOP, param.navigationElement) {
        override fun checkType(type: PsiType, context: GroovyPsiElement): Boolean = TypesUtil.isAssignableByParameter(param.type, type, context)
      } }
      navigationElement = parameterList
    }
    addMethod(mapConstructor)
  }

  private fun TransformationContext.performClassTransformation() {
    val modifierList = codeClass.modifierList
    if (modifierList != null) {
      addModifier(modifierList, GrModifier.FINAL)
      if (this.codeClass.containingClass != null) {
        addModifier(modifierList, GrModifier.STATIC)
      }
    }
  }

  private fun TransformationContext.generateRecordProperty(field: GrField) {
    addMethod(GrAccessorMethodImpl(field, false, field.name, GrModifierFlags.FINAL_MASK or getVisibilityMask(field)))
  }

  private fun getVisibilityMask(owner: PsiModifierListOwner): Int {
    return if (owner.modifierList?.hasExplicitModifier(GrModifier.PRIVATE) == true) GrModifierFlags.PRIVATE_MASK
    else if (owner.modifierList?.hasExplicitModifier(GrModifier.PROTECTED) == true) GrModifierFlags.PROTECTED_MASK
    else GrModifierFlags.PUBLIC_MASK
  }
}