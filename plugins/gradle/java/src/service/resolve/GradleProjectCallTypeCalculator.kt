// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACT_HANDLER
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.typing.GrCallTypeCalculator

class GradleProjectCallTypeCalculator : GrCallTypeCalculator {

  override fun getType(receiver: PsiType?, method: PsiMethod, arguments: Arguments?, context: PsiElement): PsiType? {
    if (receiver !is GradleProjectAwareType) return null
    if (method.name == "getProject") {
      return receiver
    }
    else if (method.name == "getArtifacts") {
      return GradleProjectAwareType(GRADLE_API_ARTIFACT_HANDLER, context)
    }
    return null
  }
}
