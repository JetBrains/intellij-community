// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.json

import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolApiStatus
import com.intellij.webSymbols.WebSymbolQualifiedName
import com.intellij.webSymbols.WebSymbolTypeSupport
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.webSymbols.customElements.CustomElementsSymbol
import com.intellij.webSymbols.customElements.impl.*
import com.intellij.webSymbols.impl.StaticWebSymbolsScopeBase
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.WebSymbolsPatternFactory
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor

fun Reference.createPattern(origin: CustomElementsJsonOrigin): WebSymbolsPattern? =
  createQueryPathList(origin)?.let { WebSymbolsPatternFactory.createSingleSymbolReferencePattern(it) }

fun Reference.resolve(origin: CustomElementsJsonOrigin, queryExecutor: WebSymbolsQueryExecutor): List<WebSymbol> =
  createQueryPathList(origin)
    ?.let { queryExecutor.runNameMatchQuery(it) }
  ?: emptyList()

private fun Reference.createQueryPathList(origin: CustomElementsJsonOrigin): List<WebSymbolQualifiedName>? {
  val pkg = `package` ?: origin.library
  val module = module ?: ""
  val refName = name ?: return null
  return listOf(
    WebSymbolQualifiedName(CustomElementsSymbol.NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, CustomElementsSymbol.KIND_CEM_PACKAGES, pkg),
    WebSymbolQualifiedName(CustomElementsSymbol.NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, CustomElementsSymbol.KIND_CEM_MODULES, module),
    WebSymbolQualifiedName(CustomElementsSymbol.NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, CustomElementsSymbol.KIND_CEM_DECLARATIONS, refName)
  )
}

fun Deprecated?.toApiStatus(origin: CustomElementsJsonOrigin): WebSymbolApiStatus? =
  this?.value?.let { msg -> WebSymbolApiStatus.Deprecated((msg as? String)?.let { origin.renderDescription(it) }) }

fun CustomElementsManifest.adaptAllContributions(origin: CustomElementsJsonOrigin, rootScope: CustomElementsManifestScopeBase)
  : Sequence<StaticWebSymbolsScopeBase.StaticSymbolContributionAdapter> =
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
  Sequence<StaticWebSymbolsScopeBase.StaticSymbolContributionAdapter> =
  (attributes.asSequence().mapNotNull { CustomElementsAttributeSymbol.create(it, origin) }
   + cssParts.asSequence().mapNotNull { CustomElementsCssPartSymbol.create(it, origin) }
   + cssProperties.asSequence().mapNotNull { CustomElementsCssCustomPropertySymbol.create(it, origin) }
   + events.asSequence().mapNotNull { CustomElementsEventSymbol.create(it, origin) }
   + members.asSequence().mapNotNull { CustomElementsMemberSymbol.create(it, origin) }
   + slots.asSequence().mapNotNull { CustomElementsSlotSymbol.create(it, origin) }
  )

fun ClassMethod.buildFunctionType(): List<WebSymbolTypeSupport.TypeReference> =
  if (parameters.isEmpty() && `return` == null)
    emptyList()
  else
    listOf(WebSymbolTypeSupport.TypeReference.create(
      null,
      "(" + parameters.asSequence()
        .mapIndexed { index, parameter ->
          (parameter.name ?: "param$index") + ": " + (parameter.type?.text ?: "any")
        }
        .joinToString() +
      ") => ${`return`?.type?.text ?: "any"}"))

fun Type.mapToReferenceList(): List<WebSymbolTypeSupport.TypeReference> =
  this.text?.let {
    listOf(WebSymbolTypeSupport.TypeReference.create(null, it))
  } ?: emptyList()