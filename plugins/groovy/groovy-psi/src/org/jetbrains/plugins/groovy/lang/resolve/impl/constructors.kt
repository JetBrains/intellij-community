// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.util.elementInfo
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.processNonCodeMembers
import org.jetbrains.plugins.groovy.lang.resolve.getDefaultConstructor
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind
import org.jetbrains.plugins.groovy.lang.resolve.sorryCannotKnowElementKind
import java.util.*

fun getAllConstructors(clazz: PsiClass, place: PsiElement): List<PsiMethod> {
  return classConstructors(clazz) +
         runtimeConstructors(clazz, place)
}

private fun classConstructors(clazz: PsiClass): List<PsiMethod> {
  val constructors = clazz.constructors
  if (constructors.isEmpty()) {
    return listOf(getDefaultConstructor(clazz))
  }
  else {
    return Arrays.asList(*constructors)
  }
}

private fun runtimeConstructors(clazz: PsiClass, place: PsiElement): List<PsiMethod> {
  val name = clazz.name ?: return emptyList()
  val processor = ConstructorProcessor(name)
  val qualifierType = JavaPsiFacade.getElementFactory(clazz.project).createType(clazz)
  processNonCodeMembers(qualifierType, processor, place, ResolveState.initial())
  return processor.candidates
}

private class ConstructorProcessor(private val name: String) : ProcessorWithHints(), NameHint, GroovyResolveKind.Hint, ElementClassHint {

  init {
    hint(NameHint.KEY, this)
    hint(GroovyResolveKind.HINT_KEY, this)
    hint(ElementClassHint.KEY, this)
  }

  override fun getName(state: ResolveState): String? = name

  override fun shouldProcess(kind: GroovyResolveKind): Boolean = kind == GroovyResolveKind.METHOD

  override fun shouldProcess(kind: ElementClassHint.DeclarationKind): Boolean = kind == ElementClassHint.DeclarationKind.METHOD

  private val myCandidates = SmartList<PsiMethod>()

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (element !is PsiMethod) {
      if (state[sorryCannotKnowElementKind] == true) {
        return true
      }
      else {
        error("Unexpected element. ${elementInfo(element)}")
      }
    }
    if (!element.isConstructor) {
      return true
    }
    myCandidates += element
    return true
  }

  val candidates: List<PsiMethod> get() = myCandidates
}
