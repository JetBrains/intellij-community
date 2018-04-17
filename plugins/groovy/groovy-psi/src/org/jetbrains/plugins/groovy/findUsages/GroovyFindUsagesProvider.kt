// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages

import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.*
import com.intellij.psi.util.PsiFormatUtil.*
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable
import org.jetbrains.plugins.groovy.refactoring.rename.PropertyForRename

open class GroovyFindUsagesProvider : FindUsagesProvider {

  override fun getWordsScanner(): WordsScanner? = GroovyWordsScanner()

  override fun canFindUsagesFor(psiElement: PsiElement): Boolean = psiElement is PsiClass ||
                                                                   psiElement is PsiMethod ||
                                                                   psiElement is GrVariable

  override fun getHelpId(psiElement: PsiElement): String? = null

  override fun getType(element: PsiElement): String = when (element) {
  // classes
    is GrTraitTypeDefinition -> message("groovy.term.trait")
    is GrInterfaceDefinition -> message("groovy.term.interface")
    is GrAnnotationTypeDefinition -> message("groovy.term.annotation")
    is GrEnumTypeDefinition -> message("groovy.term.enum")
    is GrClassDefinition -> message("groovy.term.class")
  // members
    is GrMethod -> message("groovy.term.method")
    is GrField -> if (element.isProperty) message("groovy.term.property") else message("groovy.term.field")
  // variables
    is GrParameter -> message("groovy.term.parameter")
    is GrBindingVariable -> message("groovy.term.binding")
    is GrVariable -> message("groovy.term.variable")
  // other
    is GrLabeledStatement -> message("groovy.term.label")
    is GrClosableBlock -> message("groovy.term.closure")
    is GrExpression -> message("groovy.term.expression")
    else -> ""
  }

  override fun getDescriptiveName(element: PsiElement): String {
    val descriptiveName = when (element) {
      is PsiClass -> element.qualifiedName
      is PsiMethod -> {
        val method = formatMethod(element, PsiSubstitutor.EMPTY, SHOW_NAME or SHOW_PARAMETERS, SHOW_TYPE)
        val clazz = element.containingClass
        if (clazz == null) {
          method
        }
        else {
          "$method of ${getDescriptiveName(clazz)}"
        }
      }
      is PsiVariable -> element.name
      is GrLabeledStatement -> element.name
      is PropertyForRename -> element.propertyName
      is GrLiteral -> element.text
      else -> null
    }
    return descriptiveName ?: ""
  }

  override fun getNodeText(element: PsiElement, useFullName: Boolean): String {
    val nodeText = when (element) {
      is PsiClass -> if (useFullName) element.qualifiedName ?: element.name else element.name
      is PsiMethod -> formatMethod(element, PsiSubstitutor.EMPTY, SHOW_NAME or SHOW_PARAMETERS, SHOW_TYPE)
      is PsiVariable -> element.name
      else -> null
    }
    return nodeText ?: ""
  }
}
