// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import groovy.lang.Closure
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.api.JustTypeArgument
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getDelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessLocals

internal fun GrFunctionalExpression.processParameters(processor: PsiScopeProcessor, state: ResolveState): Boolean {
  if (!processor.shouldProcessLocals()) return true

  for (parameter in allParameters) {
    if (!ResolveUtil.processElement(processor, parameter, state)) return false
  }

  return true
}

internal fun GrFunctionalExpression.processClosureClassMembers(processor: PsiScopeProcessor,
                                                               state: ResolveState,
                                                               lastParent: PsiElement?,
                                                               place: PsiElement): Boolean {
  val closureClass = JavaPsiFacade.getInstance(project).findClass(GROOVY_LANG_CLOSURE, resolveScope) ?: return true
  val newState = state.put(ClassHint.RESOLVE_CONTEXT, this)
  return ResolveUtil.processClassDeclarations(closureClass, processor, newState, lastParent, place)
}

private fun GrFunctionalExpression.processOwner(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  val parent = parent ?: return true

  if (!ResolveUtil.processStaticImports(processor, containingFile, state, place)) {
    return false
  }
  else {
    return ResolveUtil.treeWalkUp(parent, place, processor, state)
  }
}

internal fun GrFunctionalExpression.processOwnerAndDelegate(processor: PsiScopeProcessor,
                                                            state: ResolveState,
                                                            place: PsiElement): Boolean {
  val result = processDelegatesTo(processor, state, place)
  if (result != null) return result

  return processOwner(processor, state, place)
}

internal fun GrFunctionalExpression.processDeclarationsWithCallsite(processor: PsiScopeProcessor,
                                                                    state: ResolveState,
                                                                    lastParent: PsiElement?,
                                                                    place: PsiElement): Boolean {
  return processDeclarations(processor, state, lastParent, place) && processOwnerAndDelegate(processor, state, place)
}

private fun GrFunctionalExpression.processDelegatesTo(processor: PsiScopeProcessor,
                                                      state: ResolveState,
                                                      place: PsiElement): Boolean? {
  val info = getDelegatesToInfo(this) ?: return null

  when (info.strategy) {
    Closure.OWNER_FIRST -> return processOwner(processor, state, place) && processDelegate(processor, state, place, info.typeToDelegate)
    Closure.DELEGATE_FIRST -> return processDelegate(processor, state, place, info.typeToDelegate) && processOwner(processor, state, place)
    Closure.OWNER_ONLY -> return processOwner(processor, state, place)
    Closure.DELEGATE_ONLY -> return processDelegate(processor, state, place, info.typeToDelegate)
    Closure.TO_SELF -> return true
    else -> return null
  }
}

private fun GrFunctionalExpression.processDelegate(processor: PsiScopeProcessor,
                                                   state: ResolveState,
                                                   place: PsiElement,
                                                   classToDelegate: PsiType?): Boolean {
  if (classToDelegate == null) return true

  val delegateState = state.put(ClassHint.RECEIVER, JustTypeArgument(classToDelegate)).put(ClassHint.RESOLVE_CONTEXT, this)
  return ResolveUtil.processAllDeclarations(classToDelegate, processor, delegateState, place)
}

internal fun GrFunctionalExpression.doGetOwnerType(): PsiType? {
  val context = PsiTreeUtil.getParentOfType(this, GrTypeDefinition::class.java, GrFunctionalExpression::class.java, GroovyFile::class.java)
  val factory = JavaPsiFacade.getInstance(project).elementFactory
  if (context is GrTypeDefinition) {
    return factory.createType(context)
  }
  else if (context is GrFunctionalExpression) {
    return context.type
  }
  else if (context is GroovyFile) {
    val scriptClass = context.scriptClass
    if (scriptClass != null && GroovyNamesUtil.isIdentifier(scriptClass.name)) return factory.createType(scriptClass)
  }

  return null
}
