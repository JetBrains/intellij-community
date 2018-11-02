// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.autoimplement

import com.intellij.psi.HierarchicalMethodSignature
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.infos.CandidateInfo
import com.intellij.psi.util.MethodSignature
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext
import org.jetbrains.plugins.groovy.transformations.plusAssign
import org.jetbrains.plugins.groovy.util.GroovyOverrideImplementExploreUtil

internal const val autoImplementFqn = GroovyCommonClassNames.GROOVY_TRANSFORM_AUTOIMPLEMENT
internal const val autoImplementOriginInfo = "by @AutoImplement"

class AutoImplementTransformation : AstTransformationSupport {

  override fun applyTransformation(context: TransformationContext) {
    val annotation = context.getAnnotation(autoImplementFqn) ?: return

    val candidateInfo = getMapToOverrideImplement(context).values


    for (info in candidateInfo) {
      val toImplementMethod = info.element as? PsiMethod ?: continue

      val substitutor = toImplementMethod.containingClass?.let {
        TypeConversionUtil.getClassSubstitutor(it, context.codeClass, PsiSubstitutor.EMPTY)
      } ?: info.substitutor


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

        for ((index, value) in signature.parameterTypes.withIndex()) {
          addParameter(toImplementMethod.parameterList.parameters[index].name ?: ("var" + index), value)
        }
      }
    }
  }

  fun getMapToOverrideImplement(context: TransformationContext): Map<MethodSignature, CandidateInfo> {
    val signatures = ContainerUtil.newArrayList<HierarchicalMethodSignature>()

    context.superTypes.forEach {
      it.resolve()?.visibleSignatures?.let {
        signatures.addAll(it)
      }
    }

    return GroovyOverrideImplementExploreUtil.getMapToOverrideImplement(context.codeClass, signatures, true, false)
  }

}