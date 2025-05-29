// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.json

import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolApiStatus
import com.intellij.webSymbols.PolySymbolQualifiedName
import com.intellij.webSymbols.PolySymbolTypeSupport
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.webSymbols.customElements.CustomElementsSymbol
import com.intellij.webSymbols.customElements.impl.*
import com.intellij.webSymbols.impl.StaticPolySymbolsScopeBase
import com.intellij.webSymbols.patterns.PolySymbolsPattern
import com.intellij.webSymbols.patterns.PolySymbolsPatternFactory
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor

fun Reference.createPattern(origin: CustomElementsJsonOrigin): PolySymbolsPattern? =
  createQueryPathList(origin)?.let { PolySymbolsPatternFactory.createSingleSymbolReferencePattern(it) }

fun Reference.resolve(origin: CustomElementsJsonOrigin, queryExecutor: WebSymbolsQueryExecutor): List<PolySymbol> =
  createQueryPathList(origin)
    ?.let { queryExecutor.runNameMatchQuery(it) }
  ?: emptyList()

private fun Reference.createQueryPathList(origin: CustomElementsJsonOrigin): List<PolySymbolQualifiedName>? {
  val pkg = `package` ?: origin.library
  val module = module ?: ""
  val refName = name ?: return null
  return listOf(
    PolySymbolQualifiedName(CustomElementsSymbol.NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, CustomElementsSymbol.KIND_CEM_PACKAGES, pkg),
    PolySymbolQualifiedName(CustomElementsSymbol.NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, CustomElementsSymbol.KIND_CEM_MODULES, module),
    PolySymbolQualifiedName(CustomElementsSymbol.NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, CustomElementsSymbol.KIND_CEM_DECLARATIONS, refName)
  )
}

fun Deprecated?.toApiStatus(origin: CustomElementsJsonOrigin): PolySymbolApiStatus? =
  this?.value?.let { msg -> PolySymbolApiStatus.Deprecated((msg as? String)?.let { origin.renderDescription(it) }) }

fun CustomElementsManifest.adaptAllContributions(origin: CustomElementsJsonOrigin, rootScope: CustomElementsManifestScopeBase)
  : Sequence<StaticPolySymbolsScopeBase.StaticSymbolContributionAdapter> =
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
  Sequence<StaticPolySymbolsScopeBase.StaticSymbolContributionAdapter> =
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