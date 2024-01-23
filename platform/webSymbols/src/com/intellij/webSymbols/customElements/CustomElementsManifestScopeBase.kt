// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements

import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import com.intellij.webSymbols.ContextKind
import com.intellij.webSymbols.FrameworkId
import com.intellij.webSymbols.WebSymbolTypeSupport
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContextKindRules
import com.intellij.webSymbols.customElements.json.*
import com.intellij.webSymbols.impl.StaticWebSymbolsScopeBase
import com.intellij.webSymbols.query.WebSymbolNameConversionRules
import com.intellij.webSymbols.query.WebSymbolNameConversionRulesProvider

abstract class CustomElementsManifestScopeBase :
  StaticWebSymbolsScopeBase<CustomElementsManifest, Any, CustomElementsJsonOrigin>() {

  private val registeredContexts = mutableSetOf<String>()

  abstract override fun createPointer(): Pointer<out CustomElementsManifestScopeBase>

  protected open fun addCustomElementsManifest(manifest: CustomElementsManifest, origin: CustomElementsJsonOrigin) {
    if (!registeredContexts.add(origin.library))
      throw IllegalStateException("Manifest for ${origin.library} is already registered.")
    addRoot(manifest, origin)
  }

  protected open fun removeCustomElementsManifest(manifest: CustomElementsManifest) {
    registeredContexts.remove(getRootOrigin(manifest)?.library)
    removeRoot(manifest)
  }

  override fun adaptAllRootContributions(root: CustomElementsManifest,
                                         framework: FrameworkId?,
                                         origin: CustomElementsJsonOrigin): Sequence<StaticSymbolContributionAdapter> =
    root.adaptAllContributions(origin, this)

  override fun adaptAllContributions(contribution: Any,
                                     framework: FrameworkId?,
                                     origin: CustomElementsJsonOrigin): Sequence<StaticSymbolContributionAdapter> =
    when (contribution) {
      is CustomElementsPackage -> contribution.adaptAllContributions(origin, this)
      is JavaScriptModule -> contribution.adaptAllContributions(origin, this)
      is CustomElementClassOrMixinDeclaration -> contribution.adaptAllContributions(origin)
      else -> emptySequence()
    }

  override fun matchContext(origin: CustomElementsJsonOrigin, context: WebSymbolsContext): Boolean =
    true

  override fun getContextRules(): MultiMap<ContextKind, WebSymbolsContextKindRules> = MultiMap.empty()

  override fun getNameConversionRulesProvider(framework: FrameworkId): WebSymbolNameConversionRulesProvider =
    object : WebSymbolNameConversionRulesProvider {
      override fun getNameConversionRules(): WebSymbolNameConversionRules = WebSymbolNameConversionRules.empty()
      override fun createPointer(): Pointer<out WebSymbolNameConversionRulesProvider> = Pointer.hardPointer(this)
      override fun getModificationCount(): Long = 0
    }

  protected class CustomElementsManifestJsonOriginImpl(
    override val library: String,
    private val project: Project?,
    override val version: String? = null,
    override val typeSupport: WebSymbolTypeSupport? = null,
    private val sourceSymbolResolver: (source: SourceReference, cacheHolder: UserDataHolderEx) -> PsiElement? = { _, _ -> null },
  ) : CustomElementsJsonOrigin {

    override val framework: FrameworkId? = null

    override fun resolveSourceSymbol(source: SourceReference, cacheHolder: UserDataHolderEx): PsiElement? =
      sourceSymbolResolver(source, cacheHolder)

    override fun renderDescription(description: String): String =
      DocMarkdownToHtmlConverter.convert(description, project)

    override fun toString(): String {
      return "$library@$version"
    }

  }

}