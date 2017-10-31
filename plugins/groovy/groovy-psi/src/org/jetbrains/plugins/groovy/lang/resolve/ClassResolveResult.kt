// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor

class ClassResolveResult(
  element: PsiClass,
  place: PsiElement?,
  resolveContext: PsiElement?,
  substitutor: PsiSubstitutor
) : BaseGroovyResolveResult<PsiClass>(element, place, resolveContext, substitutor)
