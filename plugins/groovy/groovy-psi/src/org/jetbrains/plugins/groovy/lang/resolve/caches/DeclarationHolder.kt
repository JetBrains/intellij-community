// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.caches

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor

interface DeclarationHolder {

  fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean
}
