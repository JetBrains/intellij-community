// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.util.checkKind
import org.jetbrains.plugins.groovy.lang.psi.util.getAccessorName
import org.jetbrains.plugins.groovy.lang.resolve.AccessorResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.GenericAccessorResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.JustTypeArgument
import org.jetbrains.plugins.groovy.lang.resolve.imports.importedNameKey

class AccessorProcessor(
  propertyName: String,
  private val propertyKind: PropertyKind,
  private val arguments: Arguments?,
  private val place: PsiElement
) : ProcessorWithCommonHints(), GrResolverProcessor<GroovyResolveResult> {

  @Deprecated("don't use this constructor")
  constructor(
    propertyName: String,
    propertyKind: PropertyKind,
    arguments: () -> Array<PsiType?>?,
    place: PsiElement
  ) : this(propertyName, propertyKind, arguments()?.map { JustTypeArgument(it) }, place)

  private val accessorName = propertyKind.getAccessorName(propertyName)

  init {
    nameHint(accessorName)
    elementClassHint(ElementClassHint.DeclarationKind.METHOD)
  }

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (element !is PsiMethod) return true

    val elementName = state[importedNameKey] ?: element.name
    if (elementName != accessorName) return true
    if (!element.checkKind(propertyKind)) return true

    myResults += if (element.hasTypeParameters()) {
      GenericAccessorResolveResult(element, place, state, arguments)
    }
    else {
      AccessorResolveResult(element, place, state, arguments)
    }

    return true
  }

  private val myResults = SmartList<GroovyResolveResult>()

  override val results: List<GroovyResolveResult> get() = myResults
}
