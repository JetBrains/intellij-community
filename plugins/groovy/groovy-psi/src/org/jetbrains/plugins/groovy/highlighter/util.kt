// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.highlighter

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

internal fun PsiFile.getGroovyFile(): GroovyFileBase? = viewProvider.getPsi(GroovyLanguage) as? GroovyFileBase

internal fun PsiMethod.isMethodWithLiteralName() = this is GrMethod && nameIdentifierGroovy.isStringNameElement()

internal fun GrReferenceElement<*>.isReferenceWithLiteralName() = referenceNameElement.isStringNameElement()

private fun PsiElement?.isStringNameElement() = this?.node?.elementType in TokenSets.STRING_LITERAL_SET

internal fun GrReferenceElement<*>.isAnonymousClassReference(): Boolean {
  return (parent as? GrAnonymousClassDefinition)?.baseClassReferenceGroovy == this
}

internal fun PsiElement.isThisOrSuper() = node?.elementType?.let {
  it == GroovyTokenTypes.kTHIS || it == GroovyTokenTypes.kSUPER
} ?: false
