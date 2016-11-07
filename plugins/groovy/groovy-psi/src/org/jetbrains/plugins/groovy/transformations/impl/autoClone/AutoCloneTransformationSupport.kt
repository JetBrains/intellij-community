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
package org.jetbrains.plugins.groovy.transformations.impl.autoClone

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.impl.light.LightMethodBuilder
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.findDeclaredDetachedValue
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext
import org.jetbrains.plugins.groovy.transformations.plusAssign

class AutoCloneTransformationSupport : AstTransformationSupport {

  private companion object {
    val AUTO_CLONE_FQN = "groovy.transform.AutoClone"
    val ORIGIN_INFO = "created by @AutoClone"
    val CNSE_FQN = "java.lang.CloneNotSupportedException"
  }

  override fun applyTransformation(context: TransformationContext) {
    val annotation = context.getAnnotation(AUTO_CLONE_FQN) ?: return

    context.addInterface(CommonClassNames.JAVA_LANG_CLONEABLE)

    // public T clone() throws CloneNotSupportedException
    context += LightMethodBuilder(context.manager, "clone").apply {
      addModifier(PsiModifier.PUBLIC)
      setMethodReturnType(TypesUtil.createType(context.codeClass))
      addException(CNSE_FQN)
      navigationElement = annotation
      originInfo = ORIGIN_INFO
    }

    val value = annotation.findDeclaredDetachedValue("style") as? GrReferenceExpression ?: return
    val constant = value.resolve() as? PsiEnumConstant ?: return
    if (constant.containingClass?.qualifiedName != "groovy.transform.AutoCloneStyle") return
    when (constant.name) {
      "COPY_CONSTRUCTOR" -> {
        if (context.codeClass.codeConstructors.isEmpty()) {
          context += LightMethodBuilder(context.codeClass, GroovyLanguage).apply {
            isConstructor = true
            addModifier(PsiModifier.PUBLIC)
            navigationElement = context.codeClass
          }
        }

        // protected T(T other)
        context += LightMethodBuilder(context.codeClass, GroovyLanguage).apply {
          isConstructor = true
          addModifier(PsiModifier.PROTECTED)
          addParameter("other", TypesUtil.createType(context.codeClass))
          navigationElement = context.codeClass
          originInfo = ORIGIN_INFO
        }
      }
      "SIMPLE" -> {
        // protected void cloneOrCopyMembers(T other) throws CloneNotSupportedException
        context += LightMethodBuilder(context.manager, "cloneOrCopyMembers").apply {
          addModifier(PsiModifier.PROTECTED)
          setMethodReturnType(PsiType.VOID)
          addParameter("other", TypesUtil.createType(context.codeClass))
          addException(CNSE_FQN)
          navigationElement = annotation
          originInfo = ORIGIN_INFO
        }
      }
    }
  }
}
