// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageHandler
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.json.JsonLanguage
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.model.Pointer
import com.intellij.model.Pointer.delegatingPointer
import com.intellij.model.SingleTargetReference
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.model.psi.impl.allDeclarationsInElement
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchRequest
import com.intellij.model.search.SearchService
import com.intellij.model.search.SearchWordQueryBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.ElementManipulators
import com.intellij.psi.ElementManipulators.getValueTextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.walkUp
import com.intellij.refactoring.rename.api.PsiModifiableRenameUsage.Companion.defaultPsiModifiableRenameUsage
import com.intellij.refactoring.rename.api.PsiRenameUsage
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.RenameUsage
import com.intellij.refactoring.rename.api.RenameUsageSearchParameters
import com.intellij.refactoring.rename.api.RenameUsageSearcher
import com.intellij.util.Query
import org.jetbrains.annotations.ApiStatus

private const val COLORS_BLOCK_KEY = "colors"

@ApiStatus.Internal
class ThemeColorKey(
  val colorKey: String,
  val jsonProperty: JsonProperty?,
) : Symbol, RenameTarget, SearchTarget, NavigationTarget, DocumentationTarget {
  override fun createPointer(): Pointer<ThemeColorKey> {
    if (jsonProperty == null) return Pointer.hardPointer(this)
    return delegatingPointer(SmartPointerManager.createPointer(jsonProperty)) {
      ThemeColorKey(it.name, it)
    }
  }
  override fun computePresentation(): TargetPresentation = presentation()
  override fun navigationRequest(): NavigationRequest? {
    if (jsonProperty == null) return null

    return NavigationRequest.sourceNavigationRequest(jsonProperty.containingFile, jsonProperty.textRange)
  }

  override val usageHandler: UsageHandler
    get() = UsageHandler.createEmptyUsageHandler(colorKey)
  override val targetName: String
    get() = colorKey
  override val maximalSearchScope: SearchScope?
    get() = null

  override fun presentation(): TargetPresentation {
    return TargetPresentation.builder(DevKitThemesBundle.message("theme.color.key", colorKey)).presentation()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ThemeColorKey

    return colorKey == other.colorKey
  }

  override fun hashCode(): Int = colorKey.hashCode()

  override fun toString(): String {
    return "ThemeColorKey('$colorKey')"
  }

  override fun computeDocumentationHint(): @NlsContexts.HintText String {
    return DevKitThemesBundle.message("theme.color.key", colorKey)
  }
}

internal class ThemeColorKeyDeclaration(private val element: JsonProperty, private val symbol: ThemeColorKey) : PsiSymbolDeclaration {
  override fun getDeclaringElement(): PsiElement = element
  override fun getRangeInDeclaringElement(): TextRange {
    val originalRange = element.nameElement.textRangeInParent
    if (originalRange.length < 2) return originalRange

    return originalRange.shiftRight(1).grown(-2) // drop quotes
  }
  override fun getSymbol(): Symbol = symbol
}

internal class ThemeColorKeyReference(
  private val hostElement: JsonStringLiteral,
  val isSoft: Boolean = false,
) : PsiSymbolReference, SingleTargetReference() {

  override fun getElement(): PsiElement = hostElement
  override fun getRangeInElement(): TextRange = getValueTextRange(hostElement)

  override fun resolveSingleTarget(): Symbol? {
    val containingFile = getElement().getContainingFile()
    if (containingFile !is JsonFile) return null

    val colorName = ElementManipulators.getValueText(hostElement)
    if (colorName.isBlank()) return null

    val namedColors = ThemeJsonUtil.getNamedColorsMap(containingFile)
    val color = namedColors[colorName]
    if (color == null) return null

    val targetColorKeyElement = color.declaration.retrieve()
    if (targetColorKeyElement !is JsonProperty) return null

    return ThemeColorKey(colorName, targetColorKeyElement)
  }
}

internal class ThemeColorKeyDeclarationProvider : PsiSymbolDeclarationProvider {
  override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
    val fileName = element.containingFile?.name ?: return emptyList()
    if (!ThemeJsonUtil.isThemeFilename(fileName)) return emptyList()

    if (element !is JsonProperty) return emptyList()
    val grandParent = element.getParent() ?: return emptyList()

    val greatGrandParent = grandParent.getParent()
    if (greatGrandParent is JsonProperty && greatGrandParent.getName() == COLORS_BLOCK_KEY) {
      return listOf(ThemeColorKeyDeclaration(element, ThemeColorKey(element.name, element)))
    }

    return emptyList()
  }
}

internal class ThemeColorKeyReferenceProvider : PsiSymbolReferenceProvider {
  private val COLOR_N_PATTERN: Regex = Regex("Color\\d+")

  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    val fileName = element.containingFile?.name ?: return emptyList()
    if (!ThemeJsonUtil.isThemeFilename(fileName)) return emptyList()

    if (element !is JsonStringLiteral) return emptyList()
    val parent = element.getParent()

    if (parent !is JsonProperty) return emptyList()
    val name = parent.getName()

    if (parent.getValue() === element) { // inside value of property
      if (ThemeColorAnnotator.isColorCode(element.getValue())) return emptyList()

      val isSoft = isSoftReferenceRequired(name)
      if (isKeyInteresting(name) || isSoft) {
        return listOf(ThemeColorKeyReference(element, isSoft))
      }

      val grandParent: PsiElement? = parent.getParent()
      if (grandParent != null) {
        val greatGrandParent = grandParent.getParent()
        if (greatGrandParent is JsonProperty) {
          val parentName = greatGrandParent.getName()
          if (COLOR_N_PATTERN.matches(parentName)
              || isKeyInteresting(parentName)
              || COLORS_BLOCK_KEY == parentName) {
            return listOf(ThemeColorKeyReference(element))
          }
        }
      }
    }

    return emptyList()
  }

  private fun isSoftReferenceRequired(keyName: String): Boolean {
    return keyName.endsWith("Border")
  }

  private fun isKeyInteresting(name: String): Boolean {
    return name.endsWith("Foreground")
           || name.endsWith("Background")
           || name.endsWith("Color")
           || name.endsWith(".foreground")
           || name.endsWith(".background")
           || name.endsWith("color")
           || "foreground" == name
           || "background" == name
           || COLOR_N_PATTERN.matches(name)
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    if (target is ThemeColorKey) {
      return listOf(SearchRequest.of(target.colorKey))
    }
    return emptyList()
  }
}

private fun findPsiUsages(element: PsiElement, symbol: ThemeColorKey, offsetInElement: Int): Sequence<PsiUsage> {
  if (element !is JsonStringLiteral) return emptySequence()

  return PsiSymbolReferenceService.getService().getReferences(element, PsiSymbolReferenceHints.offsetHint(offsetInElement))
    .asSequence()
    .filterIsInstance<ThemeColorKeyReference>()
    .filter { it.rangeInElement.containsOffset(offsetInElement) }
    .filter { ref -> ref.resolvesTo(symbol) }
    .map { PsiUsage.textUsage(it) }
}

private fun searchWordQueryBuilder(project: Project, searchScope: SearchScope, symbol: ThemeColorKey): SearchWordQueryBuilder {
  return SearchService.getInstance()
    .searchWord(project, symbol.colorKey)
    .inContexts(SearchContext.inCode(), SearchContext.inStrings())
    .inScope(searchScope)
    .caseSensitive(true)
    .inFilesWithLanguageOfKind(JsonLanguage.INSTANCE)
}

internal class ThemeColorKeySearcher : UsageSearcher {
  override fun collectSearchRequest(parameters: UsageSearchParameters): Query<out Usage>? {
    val symbol = parameters.target as? ThemeColorKey ?: return null
    return searchWordQueryBuilder(parameters.project, parameters.searchScope, symbol)
      .buildQuery(LeafOccurrenceMapper.withPointer(symbol.createPointer(), ::findReferencesToSymbol))
  }

  private fun findReferencesToSymbol(symbol: ThemeColorKey, leafOccurrence: LeafOccurrence): Collection<PsiUsage> {
    for ((element, offsetInElement) in walkUp(leafOccurrence.start, leafOccurrence.offsetInStart, leafOccurrence.scope)) {
      val foundReferences = findPsiUsages(element, symbol, offsetInElement)
        .toList()

      if (foundReferences.isNotEmpty()) return foundReferences
    }
    return emptyList()
  }
}

internal class ThemeColorKeyRenameSearcher : RenameUsageSearcher {
  override fun collectSearchRequest(parameters: RenameUsageSearchParameters): Query<out RenameUsage>? {
    val symbol = parameters.target as? ThemeColorKey ?: return null
    return searchWordQueryBuilder(parameters.project, parameters.searchScope, symbol)
      .buildQuery(LeafOccurrenceMapper.withPointer(symbol.createPointer(), ::findReferencesToSymbol))
  }

  private fun findReferencesToSymbol(symbol: ThemeColorKey, leafOccurrence: LeafOccurrence): Collection<PsiRenameUsage> {
    for ((element, offsetInElement) in walkUp(leafOccurrence.start, leafOccurrence.offsetInStart, leafOccurrence.scope)) {
      val foundReferences = findPsiRenameUsages(element, symbol, offsetInElement)
        .map { defaultPsiModifiableRenameUsage(it) }
        .toList()

      if (foundReferences.isNotEmpty()) return foundReferences
    }
    return emptyList()
  }

  private fun findPsiRenameUsages(element: PsiElement, symbol: ThemeColorKey, offsetInElement: Int): Sequence<PsiUsage> {
    if (element is JsonProperty) {
      val declaredUsages = allDeclarationsInElement(element).asSequence()
        .filterIsInstance<ThemeColorKeyDeclaration>()
        .map { PsiUsage.textUsage(it) }
        .toList()

      return declaredUsages.asSequence()
    }

    return findPsiUsages(element, symbol, offsetInElement)
  }
}

internal class ThemeColorJsonPropertyRenameVetoer : Condition<PsiElement> {
  override fun value(element: PsiElement?): Boolean {
    if (element !is JsonProperty) return false

    val fileName = element.containingFile?.name ?: return false
    if (!ThemeJsonUtil.isThemeFilename(fileName)) return false

    return allDeclarationsInElement(element).any { it is ThemeColorKeyDeclaration }
  }
}