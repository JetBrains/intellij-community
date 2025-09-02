// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements

import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.context.PolyContextKindRules
import com.intellij.polySymbols.customElements.json.*
import com.intellij.polySymbols.impl.StaticPolySymbolScopeBase
import com.intellij.polySymbols.query.PolySymbolNameConversionRules
import com.intellij.polySymbols.query.PolySymbolNameConversionRulesProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class CustomElementsManifestScopeBase :
  StaticPolySymbolScopeBase<CustomElementsManifest, Any, CustomElementsJsonOrigin>() {

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

  override fun matchContext(origin: CustomElementsJsonOrigin, context: PolyContext): Boolean =
    true

  override fun getContextRules(): MultiMap<PolyContextKind, PolyContextKindRules> = MultiMap.empty()

  override fun getNameConversionRulesProvider(framework: FrameworkId): PolySymbolNameConversionRulesProvider =
    object : PolySymbolNameConversionRulesProvider {
      override fun getNameConversionRules(): PolySymbolNameConversionRules = PolySymbolNameConversionRules.empty()
      override fun createPointer(): Pointer<out PolySymbolNameConversionRulesProvider> = Pointer.hardPointer(this)
      override fun getModificationCount(): Long = 0
    }

  protected class CustomElementsManifestJsonOriginImpl(
    override val library: String,
    private val project: Project,
    override val version: String? = null,
    override val typeSupport: PolySymbolTypeSupport? = null,
    private val sourceSymbolResolver: (source: SourceReference, cacheHolder: UserDataHolderEx) -> PsiElement? = { _, _ -> null },
  ) : CustomElementsJsonOrigin {

    override val framework: FrameworkId? = null

    override fun resolveSourceSymbol(source: SourceReference, cacheHolder: UserDataHolderEx): PsiElement? =
      sourceSymbolResolver(source, cacheHolder)

    override fun renderDescription(description: String): String =
      DocMarkdownToHtmlConverter.convert(project, description)

    override fun toString(): String {
      return "$library@$version"
    }

  }

}