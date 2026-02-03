// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.lang.jvm.actions.groupActionsByType
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

fun generateCreateMethodActions(call: GrMethodCall): List<IntentionAction> {
  val methodRequests = MethodRequestsBuilder(call).buildRequests()
  val extensions = EP_NAME.extensions
  return methodRequests.flatMap { (clazz, request) ->
    extensions.flatMap { ext ->
      ext.createAddMethodActions(clazz, request)
    }
  }.groupActionsByType(GroovyLanguage)
}

internal fun getTargetClasses(ref: GrReferenceExpression, predicate: (ref: PsiClass) -> Boolean): List<PsiClass> {
  val targetClass = QuickfixUtil.findTargetClass(ref)
  if (targetClass == null || !predicate(targetClass)) return emptyList()

  val classes = mutableListOf<PsiClass>()
  collectSupers(targetClass, classes, predicate)
  return classes
}

private fun collectSupers(psiClass: PsiClass, classes: MutableList<PsiClass>, predicate: (ref: PsiClass) -> Boolean) {
  classes.add(psiClass)

  val supers = psiClass.supers
  for (aSuper in supers) {
    if (classes.contains(aSuper)) continue
    if (predicate(aSuper)) {
      collectSupers(aSuper, classes, predicate)
    }
  }
}
