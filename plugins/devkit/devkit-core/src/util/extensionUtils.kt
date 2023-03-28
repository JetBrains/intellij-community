// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util

import com.intellij.lang.jvm.JvmClassKind
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiUtil

internal fun PsiClass.isExtensionPointImplementationCandidate(): Boolean {
  return classKind == JvmClassKind.CLASS &&
         !PsiUtil.isInnerClass(this) &&
         !PsiUtil.isLocalOrAnonymousClass(this) &&
         !PsiUtil.isAbstractClass(this)
}
