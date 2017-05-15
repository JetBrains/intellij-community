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
package org.jetbrains.plugins.groovy.transformations.autoimplement

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil
import com.intellij.psi.HierarchicalMethodSignature
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.infos.CandidateInfo
import com.intellij.psi.util.MethodSignature
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext
import org.jetbrains.plugins.groovy.transformations.plusAssign
import org.jetbrains.plugins.groovy.util.GroovyOverrideImplementExploreUtil
import java.util.*

internal val autoImplementFqn = GroovyCommonClassNames.GROOVY_TRANSFORM_AUTOIMPLEMENT
internal val autoImplementOriginInfo = "by @AutoImplement"

class AutoImplementTransformation : AstTransformationSupport {

  override fun applyTransformation(context: TransformationContext) {
    val annotation = context.getAnnotation(autoImplementFqn) ?: return

    val candidateInfo = getMapToOverrideImplement(context).values


    candidateInfo.filter { it.element is PsiMethod }.forEach {

      val toImplementMethod = it.element as PsiMethod
      val methodSubstitutor = it.substitutor

      val substitutor = toImplementMethod.containingClass?.let {
        TypeConversionUtil.getClassSubstitutor(it, context.codeClass, PsiSubstitutor.EMPTY)
      } ?: methodSubstitutor


      val signature = toImplementMethod.getSignature(substitutor)

      context += context.memberBuilder.method(toImplementMethod.name) {
        setModifiers(GrModifierFlags.PUBLIC_MASK)
        returnType = substitutor.substitute(toImplementMethod.returnType)

        navigationElement = annotation
        originInfo = autoImplementOriginInfo
        if (toImplementMethod.hasTypeParameters()) {
          toImplementMethod.typeParameters.forEach {
            typeParameterList.addParameter(it)
          }
        }

        for ((index, value) in signature.parameterTypes.withIndex()){
          addParameter(toImplementMethod.parameterList.parameters[index].name ?: ("var" + index) , value)
        }
      }
    }
  }

  fun getMapToOverrideImplement(context: TransformationContext): Map<MethodSignature, CandidateInfo> {
    val abstracts = ContainerUtil.newLinkedHashMap<MethodSignature, PsiMethod>()
    val finals = ContainerUtil.newLinkedHashMap<MethodSignature, PsiMethod>()
    val concretes = ContainerUtil.newLinkedHashMap<MethodSignature, PsiMethod>()

    val extendsTypes = context.extendsTypes
    val implementTypes = context.implementsTypes

    val signatures = ContainerUtil.newArrayList<HierarchicalMethodSignature>()

    extendsTypes.forEach {
      val visibleSignatures = it.resolve()?.visibleSignatures
      if (visibleSignatures != null) signatures.addAll(visibleSignatures)
    }

    implementTypes.forEach {
      val visibleSignatures = it.resolve()?.visibleSignatures
      if (visibleSignatures != null) signatures.addAll(visibleSignatures)
    }

    val resolveHelper = JavaPsiFacade.getInstance(context.project).resolveHelper
    for (signature in signatures) {
      val method = signature.method
      if (method is GrTraitMethod) {
        for (superSignature in signature.superSignatures) {
          GroovyOverrideImplementExploreUtil.processMethod(context.codeClass, false, abstracts, finals, concretes, resolveHelper, superSignature, superSignature.method)
        }
      }
      else {
        GroovyOverrideImplementExploreUtil.processMethod(context.codeClass, false, abstracts, finals, concretes, resolveHelper, signature, method)
      }
    }

    val result = TreeMap<MethodSignature, CandidateInfo>(OverrideImplementExploreUtil.MethodSignatureComparator())

    GroovyOverrideImplementExploreUtil.collectMethodsToImplement(context.codeClass, abstracts, finals, concretes, result)

    return result
  }
}