// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.caches

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.processInnersInHierarchyNoCache
import org.jetbrains.plugins.groovy.lang.resolve.processInnersInOutersNoCache
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint
import org.jetbrains.plugins.groovy.lang.resolve.processors.GrScopeProcessorWithHints

fun GrTypeDefinition.getInnersHierarchyCache(): DeclarationHolder {
  return CachedValuesManager.getCachedValue(this) {
    makeResult(buildCache(this::processInnersInHierarchyNoCache))
  }
}

fun GrTypeDefinition.getInnersOutersCache(): DeclarationHolder {
  return CachedValuesManager.getCachedValue(this) {
    makeResult(buildCache(this::processInnersInOutersNoCache))
  }
}

private fun <T> GrTypeDefinition.makeResult(value: T): Result<T> {
  return Result.create(value, this, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT)
}

private typealias ProcessFunction = (PsiScopeProcessor, ResolveState, PsiElement) -> Boolean

private inline fun GrTypeDefinition.buildCache(f: ProcessFunction): DeclarationHolder {
  val processor = ClassCacheBuilderProcessor()
  f(processor, ResolveState.initial(), this)
  return processor.buildCache()
}

private class ClassCacheBuilderProcessor : GrScopeProcessorWithHints(null, ClassHint.RESOLVE_KINDS_CLASS) {

  private val classMap = HashMap<String, PsiClass>()

  internal fun buildCache(): DeclarationHolder = ClassCache(classMap)

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    val clazz = element as? PsiClass ?: return true
    if (clazz is PsiTypeParameter) return true

    val name = clazz.name ?: return true
    if (name in classMap) return true

    classMap.put(name, clazz)

    return true
  }
}

private class ClassCache(private val classMap: Map<String, PsiClass>) : DeclarationHolder {

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    if (classMap.isEmpty()) return true
    val name = processor.getName(state)
    if (name == null) {
      for (clazz in classMap.values) {
        if (!processor.execute(clazz, state)) return false
      }
    }
    else {
      val clazz = classMap[name]
      if (clazz != null) {
        if (!processor.execute(clazz, state)) return false
      }
    }
    return true
  }
}
