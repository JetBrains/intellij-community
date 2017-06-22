/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.jvm.createMember

import com.intellij.jvm.JvmClass
import com.intellij.jvm.createMember.CreateJvmMemberFactory
import com.intellij.jvm.createMember.CreateJvmMethodRequest
import com.intellij.jvm.createMember.CreateMemberAction
import com.intellij.jvm.createMember.CreateMemberRequest
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils

class CreateGroovyMethodFactory : CreateJvmMemberFactory {

  override fun getActions(target: JvmClass, request: CreateMemberRequest, context: PsiElement): Collection<CreateMemberAction> {
    val definition = target as? GrTypeDefinition ?: return emptyList()
    val methodRequest = request as? CreateJvmMethodRequest ?: return emptyList()
    val methodName = methodRequest.methodName
    val factory = GroovyPsiElementFactory.getInstance(context.project)

    val explicitMethodAction = object : CreateMemberAction {

      override fun getTitle(): String = "Create method '${methodName}'"

      override fun renderMember(): PsiElement {
        val method = factory.createMethodFromText("""
${methodRequest.returnType ?: "def"} ${methodName} () {}
""")
        return definition.add(method) as PsiMethod
      }
    }

    val propertyName = GroovyPropertyUtils.getPropertyNameByGetterName(methodName, false)
    if (propertyName == null) {
      return listOf(explicitMethodAction)
    }
    else {
      val propertyAction = object : CreateMemberAction {
        override fun getTitle(): String = "Create property '${propertyName}'"

        override fun renderMember(): PsiElement {
          val property = factory.createFieldDeclarationFromText("""
  ${methodRequest.returnType ?: "def"} ${propertyName} = null
  """)
          return definition.add(property)
        }
      }
      return listOf(explicitMethodAction, propertyAction)
    }
  }
}