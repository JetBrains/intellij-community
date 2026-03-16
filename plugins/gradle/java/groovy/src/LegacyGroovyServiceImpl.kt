// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.groovy

import com.intellij.gradle.java.groovy.config.GradleFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.groovy.LegacyGroovyService
import org.jetbrains.plugins.groovy.ext.spock.isSpockSpecification


@ApiStatus.Internal
@Deprecated("This service exists for compatibility reasons. Please don't use it in new code.")
internal class LegacyGroovyServiceImpl : LegacyGroovyService {
  override fun isGradleFile(file: PsiFile): Boolean {
    return GradleFileType.isGradleFile(file)
  }

  override fun isSpockSpecification(psiClass: PsiClass): Boolean {
    return psiClass.isSpockSpecification()
  }
}
