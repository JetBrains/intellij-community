// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager.getCachedValue
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultConstructor
import org.jetbrains.plugins.groovy.lang.resolve.processors.DynamicMembersHint
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolverProcessor

@JvmField val NON_CODE = Key.create<Boolean?>("groovy.process.non.code.members")

fun initialState(processNonCodeMembers: Boolean) = ResolveState.initial().put(NON_CODE, processNonCodeMembers)

fun ResolveState.processNonCodeMembers(): Boolean = get(NON_CODE).let { it == null || it }

fun treeWalkUp(place: PsiElement, processor: PsiScopeProcessor, state: ResolveState): Boolean {
  return ResolveUtil.treeWalkUp(place, place, processor, state)
}

fun shouldProcessDynamicMethods(processor: PsiScopeProcessor): Boolean {
  return processor.getHint(DynamicMembersHint.KEY)?.shouldProcessMethods() ?: false
}

fun PsiScopeProcessor.shouldProcessDynamicProperties(): Boolean {
  return getHint(DynamicMembersHint.KEY)?.shouldProcessProperties() ?: false
}

fun PsiScopeProcessor.shouldProcessMethods(): Boolean {
  return ResolveUtil.shouldProcessMethods(getHint(ElementClassHint.KEY))
}

fun PsiScopeProcessor.shouldProcessProperties(): Boolean {
  return this is GroovyResolverProcessor && isPropertyResolve
}

fun wrapClassType(type: PsiType, context: PsiElement) = TypesUtil.createJavaLangClassType(type, context.project, context.resolveScope)

fun getDefaultConstructor(clazz: PsiClass): PsiMethod {
  return getCachedValue(clazz) {
    Result.create(DefaultConstructor(clazz), clazz)
  }
}
