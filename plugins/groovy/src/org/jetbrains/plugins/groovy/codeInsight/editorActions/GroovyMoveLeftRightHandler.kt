/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInsight.editorActions

import com.intellij.codeInsight.editorActions.moveLeftRight.MoveElementLeftRightHandler
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.getChildrenOfAnyType
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
    else -> PsiElement.EMPTY_ARRAY
  }
}