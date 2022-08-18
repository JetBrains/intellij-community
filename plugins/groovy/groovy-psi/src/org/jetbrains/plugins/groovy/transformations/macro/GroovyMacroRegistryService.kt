// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

/**
 * Macro expansions occur in compile time, so we can offer a more efficient way of determining if some call is an invocation of a Groovy macro.
 *
 * Also, macro applications depend on the *syntax* (i.e. AST nodes) of a call, not the *semantics* (i.e. types of the arguments).
 */
interface GroovyMacroRegistryService {

  fun resolveAsMacro(call: GrMethodCall): PsiMethod?

  fun getAllKnownMacros(context: PsiElement): List<PsiMethod>

}

