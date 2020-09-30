// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.DOCUMENTATION_DELEGATE_FQN
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext

private const val VETOABLE_FQN = "groovy.beans.Vetoable"
private const val VCL_FQN = "java.beans.VetoableChangeListener"
private const val VCS_FQN = "java.beans.VetoableChangeSupport"
@NonNls const val ORIGIN_INFO: String = "via @Vetoable"

class VetoableTransformationSupport : AstTransformationSupport {

  override fun applyTransformation(context: TransformationContext) {
    val clazz = context.codeClass
    if (!isApplicable(clazz)) return

    val methods = mutableListOf<GrLightMethodBuilder>()

    methods += context.memberBuilder.method("addVetoableChangeListener") {
      returnType = PsiType.VOID
      addParameter("propertyName", CommonClassNames.JAVA_LANG_STRING)
      addParameter("listener", VCL_FQN)
    }

    methods += context.memberBuilder.method("addVetoableChangeListener") {
      returnType = PsiType.VOID
      addParameter("listener", VCL_FQN)
    }

    methods += context.memberBuilder.method("removeVetoableChangeListener") {
      returnType = PsiType.VOID
      addParameter("propertyName", CommonClassNames.JAVA_LANG_STRING)
      addParameter("listener", VCL_FQN)
    }

    methods += context.memberBuilder.method("removeVetoableChangeListener") {
      returnType = PsiType.VOID
      addParameter("listener", VCL_FQN)
    }

    methods += context.memberBuilder.method("fireVetoableChange") {
      returnType = PsiType.VOID
      addParameter("propertyName", CommonClassNames.JAVA_LANG_STRING)
      addParameter("oldValue", CommonClassNames.JAVA_LANG_OBJECT)
      addParameter("newValue", CommonClassNames.JAVA_LANG_OBJECT)
    }

    val vclArrayType = PsiArrayType(TypesUtil.createType(VCL_FQN, context.codeClass))

    methods += context.memberBuilder.method("getVetoableChangeListeners") {
      returnType = vclArrayType
    }

    methods += context.memberBuilder.method("getVetoableChangeListeners") {
      returnType = vclArrayType
      addParameter("propertyName", CommonClassNames.JAVA_LANG_STRING)
    }

    for (method in methods) {
      method.addModifier(PsiModifier.PUBLIC)
      method.originInfo = ORIGIN_INFO
      method.putUserData(DOCUMENTATION_DELEGATE_FQN, VCS_FQN)
    }

    context.addMethods(methods)
  }

  private fun isApplicable(clazz: GrTypeDefinition): Boolean {
    val annotation = AnnotationUtil.findAnnotation(clazz, true, VETOABLE_FQN)
    if (annotation != null) return true

    for (method in clazz.codeFields) {
      if (AnnotationUtil.findAnnotation(method, true, VETOABLE_FQN) != null) {
        return true
      }
    }

    return false
  }
}