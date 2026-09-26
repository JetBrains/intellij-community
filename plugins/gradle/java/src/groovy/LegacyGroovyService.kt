// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.groovy

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated("This service exists for compatibility reasons. Please don't use it in new code.")
interface LegacyGroovyService {
  fun isGradleFile(file: PsiFile): Boolean
  fun isSpockSpecification(psiClass: PsiClass): Boolean
}
