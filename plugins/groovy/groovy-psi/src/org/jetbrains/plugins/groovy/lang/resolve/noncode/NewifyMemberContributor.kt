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
package org.jetbrains.plugins.groovy.lang.resolve.noncode

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.util.getParents
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

internal val newifyAnnotationFqn = "groovy.lang.Newify"

class NewifyMemberContributor : NonCodeMembersContributor() {
  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {

    val qualifier = getQualifier(place)
    for (annotation in listNewifyAnnotations(place)) {
      val newifiedClasses = GrAnnotationUtil.getClassArrayValue(annotation, "value", true)
      qualifier ?: newifiedClasses.flatMap { it.constructors.asList() }.forEach {
        ResolveUtil.processElement(processor, NewifiedConstructor(it, "by @Newify", it.name, true, arrayOf(PsiModifier.STATIC)), state)
      }
      val createNewMethods = GrAnnotationUtil.inferBooleanAttributeNotNull(annotation, "auto")
      val type = (qualifier as? GrReferenceExpression)?.resolve() as? PsiClass
      if (type != null && createNewMethods) {
        type.constructors.forEach {
          ResolveUtil.processElement(processor, NewifiedConstructor(it, "by @Newify", "new", false, arrayOf(PsiModifier.STATIC)), state)
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

  class NewifiedConstructor(val myPrototype: PsiMethod, val myOriginInfo: String, val newName: String, val asConstructor: Boolean, val modifiers:Array<String>)
    : LightMethod(myPrototype.manager, myPrototype, myPrototype.containingClass!!), OriginInfoAwareElement, PsiMirrorElement {
    override fun getPrototype(): PsiElement {
      return myPrototype
    }

    val myModifierList: LightModifierList = LightModifierList(myPrototype.manager, JavaLanguage.INSTANCE, *modifiers)


    override fun getName(): String {
      return newName
    }

    override fun getOriginInfo(): String {
      return myOriginInfo
    }

    override fun hasModifierProperty(name: String): Boolean {
      return myModifierList.hasModifierProperty(name)
    }

    override fun getModifierList(): PsiModifierList {
      return myModifierList
    }

    override fun isConstructor(): Boolean {
      return asConstructor
    }
  }
}