// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.editorActions

import com.intellij.codeInsight.editorActions.moveLeftRight.MoveElementLeftRightHandler
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.getChildrenOfAnyType
import org.jetbrains.plugins.groovy.lang.psi.api.GrExpressionList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList

class GroovyMoveLeftRightHandler : MoveElementLeftRightHandler() {

  override fun getMovableSubElements(element: PsiElement): Array<out PsiElement> = when (element) {
    is GrAnnotationArgumentList -> element.attributes
    is GrAnnotationArrayInitializer -> element.initializers
    is GrArgumentList -> element.allArguments
    is GrEnumTypeDefinition -> element.enumConstants
    is GrListOrMap -> getChildrenOfAnyType(element, GrExpression::class.java, GrNamedArgument::class.java).toTypedArray()
    is GrModifierList -> element.modifiers
    is GrParameterList -> element.parameters
    is GrReferenceList -> element.referenceElementsGroovy
    is GrTypeArgumentList -> element.typeArgumentElements
    is GrTypeParameterList -> element.typeParameters
    is GrVariableDeclaration -> element.variables
    is GrExpressionList -> element.expressions.toTypedArray()
    else -> PsiElement.EMPTY_ARRAY
  }
}
