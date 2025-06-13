// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.documentation

import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.documentation.impl.PolySymbolDocumentationTargetImpl
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls

interface PolySymbolWithDocumentation : PolySymbol {

  /**
   * An optional text, which describes the symbol purpose and usage.
   * It is rendered in the documentation popup or view.
   */
  val description: @Nls String?
    get() = null

  /**
   * Additional sections, to be rendered in the symbols’ documentation.
   * Each section should have a name, but the contents can be empty.
   */
  val descriptionSections: Map<@Nls String, @Nls String>
    get() = emptyMap()

  /**
   * An optional URL to a website with detailed symbol's documentation
   */
  val docUrl: @NlsSafe String?
    get() = null

  /**
   * If the symbol represents some property, variable or anything that can hold a value,
   * this property documents what is the default value.
   */
  val defaultValue: @NlsSafe String?
    get() = null

  /**
   * Returns a default target implementation, which uses [createDocumentation] method to render the documentation.
   */
  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    PolySymbolDocumentationTargetImpl(this, location)

  /**
   * Returns [PolySymbolDocumentation] - an interface holding information required to render documentation for the symbol.
   * By default, it's contents are build from the available Poly Symbol information.
   *
   * To customize symbols documentation, one can override the method, or implement [PolySymbolDocumentationCustomizer].
   *
   * [PolySymbolDocumentation] interface provides builder methods for customizing the documentation.
   * `with*` methods return a copy of the documentation with customized fields.
   */
  fun createDocumentation(location: PsiElement?): PolySymbolDocumentation? =
    PolySymbolDocumentation.create(this, location).build()

  override fun createPointer(): Pointer<out PolySymbolWithDocumentation>

}