// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

@Experimental
interface GrCallTypeCalculator {

  fun getType(receiver: PsiType?, method: PsiMethod, arguments: Arguments?, context: PsiElement): PsiType?
}
