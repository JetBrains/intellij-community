// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.caches

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.containers.MostlySingularMultiMap
import org.jetbrains.plugins.groovy.lang.resolve.AnnotationHint
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.imports.importedNameKey

private data class ElementWithState(val element: PsiElement, val state: ResolveState)

class FileCacheBuilderProcessor(private val annotationResolve: Boolean) : PsiScopeProcessor, ElementClassHint, AnnotationHint {

  private val declarations = MostlySingularMultiMap<String, ElementWithState>()

  fun buildCache(): DeclarationHolder = FileDeclarationsCache(declarations)

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    val namedElement = element as? PsiNamedElement ?: return true
    val name = state.get(importedNameKey) ?: namedElement.name ?: return true
    declarations.add(name, ElementWithState(element, state))
    return true
  }

  override fun <T : Any?> getHint(hintKey: Key<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return when (hintKey) {
      AnnotationHint.HINT_KEY,
      ElementClassHint.KEY -> this as T
      else -> null
    }
  }

  override fun isAnnotationResolve(): Boolean = annotationResolve

  override fun shouldProcess(kind: ElementClassHint.DeclarationKind?): Boolean {
    return !annotationResolve || kind === ElementClassHint.DeclarationKind.CLASS
  }
}

private class FileDeclarationsCache(private val declarations: MostlySingularMultiMap<String, ElementWithState>) : DeclarationHolder {

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    val declarationProcessor = { (element, cachedState): ElementWithState ->
      processor.execute(element, state.putAll(cachedState))
    }
    val name = processor.getName(state)
    return if (name == null) declarations.processAllValues(declarationProcessor) else declarations.processForKey(name, declarationProcessor)
  }
}
