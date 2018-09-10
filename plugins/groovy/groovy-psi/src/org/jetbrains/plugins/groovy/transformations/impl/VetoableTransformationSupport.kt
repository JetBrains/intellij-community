/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.transformations.impl

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.DOCUMENTATION_DELEGATE_FQN
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext

private val VETOABLE_FQN = "groovy.beans.Vetoable"
private val VCL_FQN = "java.beans.VetoableChangeListener"
private val VCS_FQN = "java.beans.VetoableChangeSupport"
val ORIGIN_INFO: String = "via @Vetoable"

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