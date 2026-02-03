// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil.substituteTypeParameter
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_GROOVY_METHODS
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

class WithTraitsTypeCalculator : GrCallTypeCalculator {

  override fun getType(receiver: PsiType?, method: PsiMethod, arguments: Arguments?, context: PsiElement): PsiType? {
    if (method.name != "withTraits" || method.containingClass?.qualifiedName != DEFAULT_GROOVY_METHODS) {
      return null
    }
    if (arguments == null || arguments.size < 2) {
      return null
    }

    val baseType = arguments.first().type
    if (baseType !is PsiClassType && baseType !is GrTraitType) {
      return null
    }

    val traits = arguments.drop(1).mapNotNullTo(SmartList()) {
      val classItem = substituteTypeParameter(it.type, JAVA_LANG_CLASS, 0, false)
      if (GrTraitUtil.isTrait(PsiTypesUtil.getPsiClass(classItem))) {
        classItem
      }
      else {
        null
      }
    }
    return GrTraitType.createTraitType(baseType, traits)
  }
}
