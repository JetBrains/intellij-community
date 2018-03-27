// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTypesUtil.getPsiClass
import com.intellij.psi.util.PsiUtil.substituteTypeParameter
import com.intellij.psi.util.parents
import org.jetbrains.plugins.groovy.dgm.GdkMethodHolder.getHolderForClass
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint

class CategoryMemberContributor : NonCodeMembersContributor() {

  override fun processDynamicElements(qualifierType: PsiType, processor: PsiScopeProcessor, place: PsiElement, state: ResolveState) {
    if (!processor.shouldProcessMethods() && !processor.shouldProcessProperties()) return

    for (parent in place.parents()) {
      if (parent is GrMember) break
      if (parent !is GrClosableBlock) continue
      val call = checkMethodCall(parent) ?: continue
      val categories = getCategoryClasses(call, parent) ?: continue
      val holders = categories.map { getHolderForClass(it, false) }
      val stateWithContext = state.put(ClassHint.RESOLVE_CONTEXT, call)
      for (category in holders) {
        if (!category.processMethods(processor, stateWithContext, qualifierType, place.project)) return
      }
    }
  }

  private fun getCategoryClasses(call: GrMethodCall, closure: GrClosableBlock): List<PsiClass>? {
    val closures = call.closureArguments
    val args = call.expressionArguments
    if (args.isEmpty()) return null

    val lastArg = closure == args.last()
    if (!lastArg && closure != closures.singleOrNull()) return null
    if (call.resolveMethod() !is GrGdkMethod) return null

    if (args.size == 1 || args.size == 2 && lastArg) {
      val tupleType = args.first().type as? GrTupleType
      tupleType?.let {
        return it.componentTypes.mapNotNull {
          getPsiClass(substituteTypeParameter(it, CommonClassNames.JAVA_LANG_CLASS, 0, false))
        }
      }
    }
    return args.mapNotNull {
      (it as? GrReferenceExpression)?.resolve() as? PsiClass
    }
  }

  private fun checkMethodCall(place: PsiElement): GrMethodCall? {
    val context = place.context
    val call = when (context) {
      is GrMethodCall -> context
      is GrArgumentList -> context.context as? GrMethodCall
      else -> null
    }
    if (call == null) return null

    val invoked = call.invokedExpression as? GrReferenceExpression
    if (invoked?.referenceName != "use") return null

    return call
  }
}