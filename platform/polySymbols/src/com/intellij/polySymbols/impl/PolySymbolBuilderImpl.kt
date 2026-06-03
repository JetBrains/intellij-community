// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.model.Symbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.documentation.PolySymbolDocumentationBuilder
import com.intellij.polySymbols.BuiltPolySymbol
import com.intellij.polySymbols.PolySymbolBuilder
import com.intellij.polySymbols.PolySymbolDeclarationSite
import com.intellij.polySymbols.impl.DependencyScope.Companion.dependencyScope
import com.intellij.polySymbols.patterns.PolySymbolPatternBuilder
import com.intellij.psi.PsiElement

internal class PolySymbolBuilderImpl(
  internal val kind: PolySymbolKind,
  internal val name: String,
) : PolySymbolDslBuilderBaseImpl(), PolySymbolBuilder {

  override val builderContext: String get() = "polySymbol"

  internal var extensionValue: Boolean = false
  internal var extensionGetter: (() -> Boolean)? = null

  internal var psiContextGetter: (() -> PsiElement?)? = null

  internal var presentationValue: TargetPresentation? = null
  internal var presentationGetter: (() -> TargetPresentation)? = null

  internal var isSearchTarget: Boolean = false
  internal var isRenameTarget: Boolean = false

  internal var documentationBuilder: (PolySymbolDocumentationBuilder.(symbol: BuiltPolySymbol, location: PsiElement?) -> Unit)? = null

  internal var navigationTargetsGetter: ((Project) -> Collection<NavigationTarget>)? = null

  internal var matchContextGetter: ((PolyContext) -> Boolean)? = null
  internal var isEquivalentToGetter: ((Symbol) -> Boolean)? = null

  private var mode: Mode = Mode.NONE
  internal var patternBuilder: (PolySymbolPatternBuilder.() -> Unit)? = null
  internal var sourceGetter: (() -> PsiElement?)? = null
  internal var declarationSiteGetter: (() -> PolySymbolDeclarationSite?)? = null

  override fun extension(value: Boolean) {
    extensionValue = value
    extensionGetter = null
  }

  override fun extension(provider: () -> Boolean) {
    checkNoPsiCapture(provider, "polySymbol.extension")
    extensionGetter = provider
  }

  override fun psiContext(value: PsiElement?) {
    if (value == null) {
      psiContextGetter = { null }
    }
    else {
      val handle = dependency(value)
      psiContextGetter = { handle.readInScope() }
    }
  }

  override fun psiContext(provider: () -> PsiElement?) {
    checkNoPsiCapture(provider, "polySymbol.psiContext")
    psiContextGetter = provider
  }

  override fun presentation(value: TargetPresentation) {
    presentationValue = value
    presentationGetter = null
  }

  override fun presentation(provider: () -> TargetPresentation) {
    checkNoPsiCapture(provider, "polySymbol.presentation")
    presentationGetter = provider
  }

  override fun searchTarget() {
    isSearchTarget = true
  }

  override fun renameTarget() {
    isRenameTarget = true
  }

  override fun documentation(
    builder: PolySymbolDocumentationBuilder.(symbol: BuiltPolySymbol, location: PsiElement?) -> Unit,
  ) {
    checkNoPsiCapture(builder, "polySymbol.documentation")
    documentationBuilder = builder
  }

  override fun navigationTargets(provider: (project: Project) -> Collection<NavigationTarget>) {
    checkNoPsiCapture(provider, "polySymbol.navigationTargets")
    navigationTargetsGetter = provider
  }

  override fun matchContext(provider: (context: PolyContext) -> Boolean) {
    checkNoPsiCapture(provider, "polySymbol.matchContext")
    matchContextGetter = provider
  }

  override fun isEquivalentTo(provider: (symbol: Symbol) -> Boolean) {
    checkNoPsiCapture(provider, "polySymbol.isEquivalentTo")
    isEquivalentToGetter = provider
  }

  override fun pattern(body: PolySymbolPatternBuilder.() -> Unit) {
    enterMode(Mode.PATTERN)
    checkNoPsiCapture(body, "polySymbol.pattern")
    patternBuilder = body
  }

  override fun linkWithPsiElement(element: PsiElement) {
    enterMode(Mode.LINK_WITH_PSI)
    val handle = dependency(element)
    sourceGetter = { handle.readInScope() }
  }

  override fun linkWithPsiElement(provider: () -> PsiElement?) {
    enterMode(Mode.LINK_WITH_PSI)
    checkNoPsiCapture(provider, "polySymbol.linkWithPsiElement")
    sourceGetter = provider
  }

  override fun declaredInPsi(element: PsiElement, range: TextRange) {
    enterMode(Mode.DECLARED_IN_PSI)
    val handle = dependency(element)
    declarationSiteGetter = {
      PolySymbolDeclarationSite(handle.readInScope(), range)
    }
  }

  override fun declaredInPsi(provider: () -> PolySymbolDeclarationSite?) {
    enterMode(Mode.DECLARED_IN_PSI)
    checkNoPsiCapture(provider, "polySymbol.declaredInPsi")
    declarationSiteGetter = provider
  }

  private fun enterMode(newMode: Mode) {
    if (mode != Mode.NONE && mode != newMode) {
      throw IllegalStateException(
        "symbol is already configured as ${mode.label}; cannot additionally apply ${newMode.label}."
      )
    }
    mode = newMode
  }

  internal fun build(): BuiltPolySymbol {
    // Mutual-exclusion + cross-mode validation.
    if (mode == Mode.LINK_WITH_PSI || mode == Mode.DECLARED_IN_PSI) {
      check(!isSearchTarget) {
        "searchTarget(...) is handled by the framework in ${mode.label} mode; do not override."
      }
      check(!isRenameTarget) {
        "renameTarget(...) is handled by the framework in ${mode.label} mode; do not override."
      }
      check(psiContextGetter == null) {
        "psiContext(...) is wired automatically in ${mode.label} mode; do not override."
      }
    }

    val source = DependencySource.fromSpecs(depSpecs.toList())
    val initialScope = source.dependencyScope()
    val config = toBuiltConfig()

    return when (mode) {
      Mode.NONE -> BuiltPolySymbolImpl(
        config, source, initialScope,
        psiContextGetter = psiContextGetter,
      )
      Mode.PATTERN -> BuiltPolySymbolWithPattern(
        config, source, initialScope,
        patternBuilder = patternBuilder ?: error("pattern was not set on PolySymbolBuilder in PATTERN mode"),
        psiContextGetter = psiContextGetter,
      )
      Mode.LINK_WITH_PSI -> BuiltPsiLinkedPolySymbol(
        config, source, initialScope,
        sourceGetter = sourceGetter ?: error("source was not set on PolySymbolBuilder in LINK_WITH_PSI mode"),
      )
      Mode.DECLARED_IN_PSI -> BuiltPolySymbolDeclaredInPsi(
        config, source, initialScope,
        declarationSiteGetter = declarationSiteGetter ?: error("declarationSite was not set on PolySymbolBuilder in DECLARED_IN_PSI mode"),
      )
    }
  }
}

internal enum class Mode(val label: String) {
  NONE("plain"),
  PATTERN("pattern"),
  LINK_WITH_PSI("linkWithPsiElement"),
  DECLARED_IN_PSI("declaredInPsi"),
}

private fun PolySymbolBuilderImpl.toBuiltConfig(): BuiltConfig {
  val flags = packFlags(isSearchTarget, isRenameTarget, extensionValue)

  val coldGettersAllNull = priorityGetter == null
                           && apiStatusGetter == null
                           && modifiersGetter == null
                           && iconGetter == null
                           && extensionGetter == null
                           && presentationGetter == null
                           && documentationBuilder == null
                           && navigationTargetsGetter == null
                           && matchContextGetter == null
                           && isEquivalentToGetter == null
  val coldValuesAllNull = apiStatusValue == null
                          && modifiersValue == null
                          && iconValue == null
                          && presentationValue == null

  if (coldGettersAllNull && coldValuesAllNull) {
    if (flags == 0
        && priorityValue == null
        && propertyValues.isEmpty()
        && propertyGetters.isEmpty()
    ) {
      return BuiltConfigMinimal(kind = kind, name = name)
    }
    return BuiltConfigSimple(
      kind = kind,
      name = name,
      priorityValue = priorityValue,
      propertyValues = propertyValues.toMap(),
      propertyGetters = propertyGetters.toMap(),
      flags = flags,
    )
  }

  return BuiltConfigFull(
    kind = kind,
    name = name,
    priorityValue = priorityValue,
    priorityGetter = priorityGetter,
    apiStatusValue = apiStatusValue,
    apiStatusGetter = apiStatusGetter,
    modifiersValue = modifiersValue,
    modifiersGetter = modifiersGetter,
    iconValue = iconValue,
    iconGetter = iconGetter,
    extensionGetter = extensionGetter,
    presentationValue = presentationValue,
    presentationGetter = presentationGetter,
    documentationBuilder = documentationBuilder,
    navigationTargetsGetter = navigationTargetsGetter,
    matchContextGetter = matchContextGetter,
    isEquivalentToGetter = isEquivalentToGetter,
    propertyValues = propertyValues.toMap(),
    propertyGetters = propertyGetters.toMap(),
    flags = flags,
  )
}
