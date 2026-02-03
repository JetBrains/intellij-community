// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.closure

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames


internal fun PsiType?.isClosureType(): Boolean {
  return this != null && equalsToText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE)
}

internal fun PsiElement?.isClosureCallMethod(): Boolean {
  return this is PsiMethod && name == "call" && containingClass?.qualifiedName == GroovyCommonClassNames.GROOVY_LANG_CLOSURE
}

internal fun GrCall?.isClosureCall(qualifier: PsiElement): Boolean = this?.resolveMethod()?.isClosureCallMethod() ?: false
                                                                     && this is GrMethodCall
                                                                     && ((this.invokedExpression as? GrReferenceExpression)
                                                                           ?.qualifierExpression
                                                                           ?.reference
                                                                           ?.resolve() == qualifier
                                                                         || this.invokedExpression.reference?.resolve() == qualifier)