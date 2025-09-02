// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.json

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.polySymbols.customElements.CustomElementsSymbol
import com.intellij.polySymbols.customElements.impl.*
import com.intellij.polySymbols.impl.StaticPolySymbolScopeBase
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.patterns.PolySymbolPatternFactory
import com.intellij.polySymbols.query.PolySymbolQueryExecutor

fun Reference.createPattern(origin: CustomElementsJsonOrigin): PolySymbolPattern? =
  createQueryPathList(origin)?.let { PolySymbolPatternFactory.createSingleSymbolReferencePattern(it) }

fun Reference.resolve(origin: CustomElementsJsonOrigin, queryExecutor: PolySymbolQueryExecutor): List<PolySymbol> =
  createQueryPathList(origin)
    ?.let { queryExecutor.nameMatchQuery(it).exclude(PolySymbolModifier.ABSTRACT).run() }
  ?: emptyList()

private fun Reference.createQueryPathList(origin: CustomElementsJsonOrigin): List<PolySymbolQualifiedName>? {
  val pkg = `package` ?: origin.library
  val module = module ?: ""
  val refName = name ?: return null
  return listOf(
    CustomElementsSymbol.CEM_PACKAGES.withName(pkg),
    CustomElementsSymbol.CEM_MODULES.withName(module),
    CustomElementsSymbol.CEM_DECLARATIONS.withName(refName)
  )
}

fun Deprecated?.toApiStatus(origin: CustomElementsJsonOrigin): PolySymbolApiStatus? =
  this?.value?.let { msg -> PolySymbolApiStatus.Deprecated((msg as? String)?.let { origin.renderDescription(it) }) }

fun CustomElementsManifest.adaptAllContributions(origin: CustomElementsJsonOrigin, rootScope: CustomElementsManifestScopeBase)
  : Sequence<StaticPolySymbolScopeBase.StaticSymbolContributionAdapter> =
  modules.asSequence().flatMap { module ->
    module.exports.asSequence()
      .filterIsInstance<CustomElementExport>()
      .mapNotNull { CustomElementsCustomElementExportSymbol.create(it, origin) }
  } + CustomElementsJavaScriptPackageSymbol(CustomElementsPackage(this), origin, rootScope)

fun CustomElementsPackage.adaptAllContributions(origin: CustomElementsJsonOrigin, rootScope: CustomElementsManifestScopeBase): Sequence<CustomElementsJavaScriptModuleSymbol> =
  manifest.modules.asSequence().mapNotNull { module ->
    CustomElementsJavaScriptModuleSymbol.create(module, origin, rootScope)
  }

fun JavaScriptModule.adaptAllContributions(origin: CustomElementsJsonOrigin, rootScope: CustomElementsManifestScopeBase): Sequence<CustomElementsClassOrMixinDeclarationAdapter> =
  declarations.asSequence()
    .filterIsInstance<CustomElementClassOrMixinDeclaration>()
    .mapNotNull { CustomElementsClassOrMixinDeclarationAdapter.create(it, origin, rootScope) }

fun CustomElementClassOrMixinDeclaration.adaptAllContributions(origin: CustomElementsJsonOrigin):
  Sequence<StaticPolySymbolScopeBase.StaticSymbolContributionAdapter> =
  (attributes.asSequence().mapNotNull { CustomElementsAttributeSymbol.create(it, origin) }
   + cssParts.asSequence().mapNotNull { CustomElementsCssPartSymbol.create(it, origin) }
   + cssProperties.asSequence().mapNotNull { CustomElementsCssCustomPropertySymbol.create(it, origin) }
   + events.asSequence().mapNotNull { CustomElementsEventSymbol.create(it, origin) }
   + members.asSequence().mapNotNull { CustomElementsMemberSymbol.create(it, origin) }
   + slots.asSequence().mapNotNull { CustomElementsSlotSymbol.create(it, origin) }
  )

fun ClassMethod.buildFunctionType(): List<PolySymbolTypeSupport.TypeReference> =
  if (parameters.isEmpty() && `return` == null)
    emptyList()
  else
    listOf(PolySymbolTypeSupport.TypeReference.create(
      null,
      "(" + parameters.asSequence()
        .mapIndexed { index, parameter ->
          (parameter.name ?: "param$index") + ": " + (parameter.type?.text ?: "any")
        }
        .joinToString() +
      ") => ${`return`?.type?.text ?: "any"}"))

fun Type.mapToReferenceList(): List<PolySymbolTypeSupport.TypeReference> =
  this.text?.let {
    listOf(PolySymbolTypeSupport.TypeReference.create(null, it))
  } ?: emptyList()