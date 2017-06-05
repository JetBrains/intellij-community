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
package org.jetbrains.plugins.groovy.lang.resolve.newify

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.getParents
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

    val qualifier = getQualifier(place)
    for (annotation in listNewifyAnnotations(place)) {
      val newifiedClasses = GrAnnotationUtil.getClassArrayValue(annotation, "value", true)

      qualifier ?: newifiedClasses.flatMap { buildConstructors(it, it.name, true) }.forEach {
        ResolveUtil.processElement(processor, it, state)
      }
      val createNewMethods = GrAnnotationUtil.inferBooleanAttributeNotNull(annotation, "auto")
      val type = (qualifier as? GrReferenceExpression)?.resolve() as? PsiClass
      if (type != null && createNewMethods) {
        val constructors = buildConstructors(type, "new", false)
        constructors.forEach {
          ResolveUtil.processElement(processor, it, state)
        }
      }
    }
  }


  fun listNewifyAnnotations(place: PsiElement): List<PsiAnnotation> {
    return place.getParents().map { it.second }.flatMap {

      val owner = it as? PsiModifierListOwner ?: return@flatMap emptySequence<PsiAnnotation>()
      val seq = owner.modifierList?.annotations?.asSequence()?.filter { it.qualifiedName == newifyAnnotationFqn }

      return@flatMap seq ?: emptySequence<PsiAnnotation>()
    }.toList()

  }

  fun getQualifier(elem: PsiElement): PsiElement? {
    return (elem as? GrReferenceExpression)?.qualifierExpression
  }

  fun buildConstructors(clazz: PsiClass, newName: String?, asConstructor: Boolean): List<NewifiedConstructor> {
    newName ?: return emptyList()
    val constructors = clazz.constructors
    if (constructors.isNotEmpty()) {
      return constructors.mapNotNull { buildNewifiedConstructor(it, newName, asConstructor) }
    }
    else {
      return listOf(buildNewifiedConstructor(clazz, newName, asConstructor))
    }
  }

  fun buildNewifiedConstructor(myPrototype: PsiMethod, newName: String, asConstructor: Boolean): NewifiedConstructor? {
    val builder = NewifiedConstructor(myPrototype.manager, newName)
    val psiClass = myPrototype.containingClass ?: return null
    builder.containingClass = psiClass
    builder.setMethodReturnType(TypesUtil.createType(psiClass))
    builder.navigationElement = myPrototype
    builder.isConstructor = asConstructor
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

  fun buildNewifiedConstructor(myPrototype: PsiClass, newName: String, asConstructor: Boolean): NewifiedConstructor {
    val builder = NewifiedConstructor(myPrototype.manager, newName)
    builder.containingClass = myPrototype
    builder.setMethodReturnType(TypesUtil.createType(myPrototype))
    builder.navigationElement = myPrototype
    builder.isConstructor = asConstructor
    return builder
  }

  class NewifiedConstructor(val myManager: PsiManager, val newName: String) : LightMethodBuilder(myManager, newName) {
    init {
      addModifier(PsiModifier.STATIC)
      originInfo = newifyOriginInfo
    }

  }
}