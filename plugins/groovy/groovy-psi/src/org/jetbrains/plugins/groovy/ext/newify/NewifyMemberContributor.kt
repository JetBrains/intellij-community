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
package org.jetbrains.plugins.groovy.ext.newify

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.parents
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil.getClassArrayValue
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

internal val newifyAnnotationFqn = "groovy.lang.Newify"
internal val newifyOriginInfo = "by @Newify"

class NewifyMemberContributor : NonCodeMembersContributor() {
  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {

    if (place !is GrReferenceExpression) return
    val newifyAnnotations = place.listNewifyAnnotations()
    if (newifyAnnotations.isEmpty()) return

    val qualifier = place.qualifierExpression
    val type = (qualifier as? GrReferenceExpression)?.resolve() as? PsiClass

    for (annotation in newifyAnnotations) {
      val newifiedClasses = getClassArrayValue(annotation, "value", true)

      qualifier ?: newifiedClasses.flatMap { buildConstructors(it, it.name) }.forEach {
        ResolveUtil.processElement(processor, it, state)
      }

      val createNewMethods = GrAnnotationUtil.inferBooleanAttributeNotNull(annotation, "auto")
      if (type != null && createNewMethods) {
          buildConstructors(type, "new").forEach {
            ResolveUtil.processElement(processor, it, state)
        }
      }
    }
  }

  private fun PsiElement.listNewifyAnnotations() = parents().flatMap {
    val owner = it as? PsiModifierListOwner
    val seq = owner?.modifierList?.annotations?.asSequence()?.filter { it.qualifiedName == newifyAnnotationFqn }
    return@flatMap seq ?: emptySequence()
  }.toList()

  fun buildConstructors(clazz: PsiClass, newName: String?): List<NewifiedConstructor> {
    newName ?: return emptyList()
    val constructors = clazz.constructors
    if (constructors.isNotEmpty()) {
      return constructors.mapNotNull { buildNewifiedConstructor(it, newName) }
    }
    else {
      return listOf(buildNewifiedConstructor(clazz, newName))
    }
  }

  private fun buildNewifiedConstructor(myPrototype: PsiMethod, newName: String): NewifiedConstructor? {
    val builder = NewifiedConstructor(myPrototype.manager, newName)
    val psiClass = myPrototype.containingClass ?: return null
    builder.containingClass = psiClass
    builder.setMethodReturnType(TypesUtil.createType(psiClass))
    builder.navigationElement = myPrototype
    myPrototype.parameterList.parameters.forEach {
      builder.addParameter(it)
    }
    myPrototype.throwsList.referencedTypes.forEach {
      builder.addException(it)
    }
    myPrototype.typeParameters.forEach {
      builder.addTypeParameter(it)
    }
    return builder
  }

  private fun buildNewifiedConstructor(myPrototype: PsiClass, newName: String): NewifiedConstructor {
    val builder = NewifiedConstructor(myPrototype.manager, newName)
    builder.containingClass = myPrototype
    builder.setMethodReturnType(TypesUtil.createType(myPrototype))
    builder.navigationElement = myPrototype
    return builder
  }

  class NewifiedConstructor(val myManager: PsiManager, val newName: String) : LightMethodBuilder(myManager, newName) {
    init {
      addModifier(PsiModifier.STATIC)
      originInfo = newifyOriginInfo
    }

  }
}