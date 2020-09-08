// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.ast.contributor

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiKeyword.SUPER
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.parentOfType
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.resolve.ClosureMemberContributor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.ast.TupleConstructorAttributes
import org.jetbrains.plugins.groovy.lang.resolve.ast.constructorGeneratingAnnotations

class SyntheticKeywordConstructorContributor : ClosureMemberContributor() {

  override fun processMembers(closure: GrClosableBlock, processor: PsiScopeProcessor, place: PsiElement, state: ResolveState) {
    if (!ResolveUtil.shouldProcessMethods(processor.getHint(ElementClassHint.KEY))) return
    val nameHint = ResolveUtil.getNameHint(processor)
    if (nameHint != null && nameHint != SUPER) return

    if (closure != place.parentOfType<GrClosableBlock>()) return
    val anno = closure.parentOfType<PsiAnnotation>()?.takeIf { it.qualifiedName in constructorGeneratingAnnotations } ?: return
    if (GrAnnotationUtil.inferClosureAttribute(anno, TupleConstructorAttributes.PRE) != closure) return


    val syntheticMethods = createSyntheticConstructors(closure)

    for (method in syntheticMethods) {
      if (!processor.execute(method, state)) {
        return
      }
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