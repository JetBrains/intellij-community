// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.util

import com.intellij.psi.*
import com.intellij.psi.util.JvmMainMethodSearcher
import com.siyeh.ig.psiutils.TypeUtils.isJavaLangObject
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

object GroovyMainMethodSearcher : JvmMainMethodSearcher() {
  /**
   * The logic of method priorities is taken from `org.codehaus.groovy.tools.shell.GroovyShell#runScriptOrMainOrTestOrRunnable`
   */
  private val mainComparator: Comparator<PsiMethod> = Comparator { o1, o2 ->


    val o1ParameterList = o1.parameterList
    val o2ParameterList = o2.parameterList

    val parametersComparison = compareParameters(o2ParameterList, o1ParameterList)
    if (parametersComparison != 0) return@Comparator parametersComparison

    val isO1Static: Boolean = o1.hasModifierProperty(PsiModifier.STATIC)
    val isO2Static: Boolean = o2.hasModifierProperty(PsiModifier.STATIC)

    return@Comparator isO2Static.compareTo(isO1Static)
  }

  private fun compareParameters(leftParameterList: PsiParameterList, rightParameterList: PsiParameterList): Int {
    val leftParametersCount = leftParameterList.parametersCount
    val rightParametersCount = rightParameterList.parametersCount
    if (leftParametersCount < rightParametersCount) return -1
    else if (leftParametersCount > rightParametersCount) return 1
    else if (leftParametersCount != 1) return 0
    else {
      val leftParameter = getParameterPriority(leftParameterList.parameters[0])
      val rightParameter = getParameterPriority(rightParameterList.parameters[0])
      return leftParameter.compareTo(rightParameter)
    }
  }

  private fun getParameterPriority(parameter: PsiParameter): Int {
    if (parameter !is GrParameter) return 0
    val groovyType = parameter.typeGroovy
    return when {
      isJavaLangObject(groovyType) || groovyType == null -> 1
      isJavaLangStringArray(parameter) -> 2
      else -> 0
    }
  }

  override fun getMainCandidateComparator(): Comparator<PsiMethod> {
    return mainComparator
  }

  override fun instanceMainMethodsEnabled(psiElement: PsiElement): Boolean {
    return GroovyConfigUtils.getInstance().isVersionAtLeast(psiElement, GroovyConfigUtils.GROOVY5_0) && psiElement.language == GroovyLanguage
  }

  override fun inheritedStaticMainEnabled(psiElement: PsiElement): Boolean {
    return GroovyConfigUtils.getInstance().isVersionAtLeast(psiElement, GroovyConfigUtils.GROOVY5_0)
  }

  override fun findMainMethodsInClassByName(aClass: PsiClass): Array<PsiMethod> {
    if (aClass is GrTypeDefinition) return aClass.findCodeMethodsByName(MAIN_METHOD_IDENTIFIER, true)
    return aClass.findMethodsByName(MAIN_METHOD_IDENTIFIER, true)
  }
}