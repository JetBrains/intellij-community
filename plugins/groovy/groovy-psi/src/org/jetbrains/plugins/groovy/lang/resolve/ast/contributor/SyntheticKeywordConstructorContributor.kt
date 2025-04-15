// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.ast.contributor

import com.intellij.java.syntax.parser.JavaKeywords.SUPER
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.parentOfType
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.ast.TupleConstructorAttributes

class SyntheticKeywordConstructorContributor : AbstractGeneratedConstructorContributor() {

  override fun failFastCheck(processor: PsiScopeProcessor, state: ResolveState): Boolean {
    if (!ResolveUtil.shouldProcessMethods(processor.getHint(ElementClassHint.KEY))) return true
    val nameHint = ResolveUtil.getNameHint(processor)
    return nameHint != null && nameHint != SUPER
  }

  override fun generateSyntheticElements(annotation: PsiAnnotation, closure: GrClosableBlock, mode: String): Iterable<PsiElement> {
    return if (mode == TupleConstructorAttributes.PRE) {
      createSyntheticConstructors(closure)
    } else {
      emptyList()
    }
  }

  private fun createSyntheticConstructors(closure: GrClosableBlock): List<GrMethod> {
    val outerClass = closure.parentOfType<PsiClass>() ?: return emptyList()
    val superClass = outerClass.superClass
    val methods = SmartList<GrMethod>()
    if (superClass != null) {
      val constructors = superClass.constructors
      if (constructors.isEmpty()) {
        val method = SyntheticKeywordConstructor(outerClass, superClass, SUPER)
        methods.add(method)
      }
      else {
        for (constructor in constructors) {
          val method = SyntheticKeywordConstructor(outerClass, superClass, SUPER)
          for (param in constructor.parameterList.parameters) {
            method.addParameter(param.name, param.type)
          }
          methods.add(method)
        }
      }
    }
    return methods
  }

  private class SyntheticKeywordConstructor(containingClass: PsiClass, superClass: PsiClass, name: String) :
    GrLightMethodBuilder(containingClass.manager, name) {
    init {
      assert(name.isReserved())
      isConstructor = true
      navigationElement = superClass
      this.containingClass = containingClass
    }
  }

  companion object {
    private fun String?.isReserved(): Boolean = this == SUPER

    @JvmStatic
    fun isSyntheticConstructorCall(call: GrMethodCall?): Boolean =
      call?.callReference?.methodName.isReserved() && call?.resolveMethod() is SyntheticKeywordConstructor
  }
}