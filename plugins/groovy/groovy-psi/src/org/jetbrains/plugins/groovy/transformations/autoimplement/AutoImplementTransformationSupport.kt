// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.autoimplement

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.infos.CandidateInfo
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext
import org.jetbrains.plugins.groovy.transformations.plusAssign
import org.jetbrains.plugins.groovy.util.GroovyOverrideImplementExploreUtil.getMapToOverrideImplement

class AutoImplementTransformation : AstTransformationSupport {

  override fun applyTransformation(context: TransformationContext) {
    val annotation = context.getAnnotation("groovy.transform.AutoImplement") ?: return
    val hierarchyView: PsiClass = context.hierarchyView
    val signatures = hierarchyView.superTypes.flatMap {
      it.resolve()?.visibleSignatures ?: emptyList()
    }
    val candidateInfos: Collection<CandidateInfo> = getMapToOverrideImplement(hierarchyView, signatures, true, false).values
    for (info in candidateInfos) {
      val toImplementMethod = info.element as? PsiMethod ?: continue

      val substitutor = toImplementMethod.containingClass?.let {
        TypeConversionUtil.getClassSubstitutor(it, hierarchyView, PsiSubstitutor.EMPTY)
      } ?: info.substitutor

      val signature = toImplementMethod.getSignature(substitutor)

      context += context.memberBuilder.method(toImplementMethod.name) {
        setModifiers(GrModifierFlags.PUBLIC_MASK)
        returnType = substitutor.substitute(toImplementMethod.returnType)
        navigationElement = annotation
        originInfo = "by @AutoImplement"

        for (typeParameter in toImplementMethod.typeParameters) {
          typeParameterList.addParameter(typeParameter)
        }

        for ((index, value) in signature.parameterTypes.withIndex()) {
          addParameter(toImplementMethod.parameterList.parameters[index].name, value)
        }
      }
    }
  }
}
