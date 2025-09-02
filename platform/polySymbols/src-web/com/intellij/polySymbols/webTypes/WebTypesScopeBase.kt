// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes

import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.EmptyIcon
import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.context.PolyContext.Companion.KIND_FRAMEWORK
import com.intellij.polySymbols.context.PolyContextKindRules
import com.intellij.polySymbols.context.PolyContextKindRules.DisablementRules
import com.intellij.polySymbols.context.PolyContextKindRules.EnablementRules
import com.intellij.polySymbols.context.PolyContextRulesProvider
import com.intellij.polySymbols.impl.StaticPolySymbolScopeBase
import com.intellij.polySymbols.query.PolySymbolNameConversionRules
import com.intellij.polySymbols.query.PolySymbolNameConversionRulesProvider
import com.intellij.polySymbols.webTypes.impl.WebTypesJsonContributionAdapter
import com.intellij.polySymbols.webTypes.impl.WebTypesJsonContributionAdapter.Companion.wrap
import com.intellij.polySymbols.webTypes.json.*
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon

@Internal
abstract class WebTypesScopeBase :
  StaticPolySymbolScopeBase<Contributions, GenericContributionsHost, WebTypesJsonOrigin>(),
  PolyContextRulesProvider {

  private val frameworkConfigs = mutableMapOf<WebTypes, FrameworkConfig>()
  private val contextsConfigs = mutableMapOf<WebTypes, ContextsConfig>()

  private val contextRulesCache: ClearableLazyValue<MultiMap<PolyContextKind, PolyContextKindRules>> = createContextRulesCache()

  private val nameConversionRulesCache = createNameConversionRulesCache()

  abstract override fun createPointer(): Pointer<out WebTypesScopeBase>

  override fun getNameConversionRulesProvider(framework: FrameworkId): PolySymbolNameConversionRulesProvider {
    return WebTypesSymbolNameConversionRulesProvider(framework, this, nameConversionRulesCache)
  }

  override fun getContextRules(): MultiMap<PolyContextKind, PolyContextKindRules> = contextRulesCache.value

  protected open fun addWebTypes(webTypes: WebTypes, context: WebTypesJsonOrigin) {
    addRoot(webTypes.contributions, context)

    val framework = context.framework
    var dropCaches = false
    if (framework != null) {
      webTypes.frameworkConfig?.let {
        frameworkConfigs[webTypes] = it
        dropCaches = true
      }
    }
    webTypes.contextsConfig?.let {
      contextsConfigs[webTypes] = it
      dropCaches = true
    }
    if (dropCaches) dropCaches()
  }

  protected open fun removeWebTypes(webTypes: WebTypes) {
    removeRoot(webTypes.contributions)

    var dropCaches = false
    if (frameworkConfigs.remove(webTypes) != null) {
      dropCaches = true
    }
    if (contextsConfigs.remove(webTypes) != null) {
      dropCaches = true
    }
    if (dropCaches) {
      dropCaches()
    }
  }

  override fun dropCaches() {
    super.dropCaches()
    contextRulesCache.drop()
    nameConversionRulesCache.drop()
  }

  override fun matchContext(origin: WebTypesJsonOrigin, context: PolyContext): Boolean =
    origin.matchContext(context)

  override fun adaptAllRootContributions(root: Contributions,
                                         framework: FrameworkId?,
                                         origin: WebTypesJsonOrigin): Sequence<WebTypesJsonContributionAdapter> =
    root.getAllContributions(framework)
      .flatMap { (qualifiedKind, list) ->
        list.map { it.wrap(origin, this@WebTypesScopeBase, qualifiedKind) }
      }

  override fun adaptAllContributions(contribution: GenericContributionsHost,
                                     framework: FrameworkId?,
                                     origin: WebTypesJsonOrigin): Sequence<WebTypesJsonContributionAdapter> =
    contribution.getAllContributions(framework)
      .flatMap { (qualifiedKind, list) ->
        list.map { it.wrap(origin, this@WebTypesScopeBase, qualifiedKind) }
      }

  private fun createContextRulesCache(): ClearableLazyValue<MultiMap<PolyContextKind, PolyContextKindRules>> {
    return ClearableLazyValue.create {
      val deprecatedContextConfigRules = contextsConfigs.values.asSequence()
        .flatMap { it.additionalProperties.entries }
        .filter { (name, config) -> name != null && config.kind != null }
        .map { (name, config) -> RulesEntry(config.kind, name, config.enableWhen?.wrap(), config.disableWhen?.wrap()) }

      val contextConfigRules = contextsConfigs.values.asSequence()
        .flatMap { it.additionalProperties.entries }
        .filter { (kind, kindConfig) -> kind != null && kindConfig.kind == null }
        .flatMap { (kind, kindConfig) ->
          kindConfig.additionalProperties.asSequence()
            .filter { it.key != null }
            .map { (name, config) ->
              RulesEntry(kind, name, config.enableWhen?.wrap(), config.disableWhen?.wrap())
            }
        }
      val frameworkConfigRules = frameworkConfigs
        .filter { (webTypes, _) -> webTypes.framework != null }
        .map { (webTypes, config) ->
          RulesEntry(KIND_FRAMEWORK, webTypes.framework, config.enableWhen?.wrap(), config.disableWhen?.wrap())
        }


      val rulesPerKind = deprecatedContextConfigRules
        .plus(contextConfigRules)
        .plus(frameworkConfigRules)
        .groupBy { it.kind }

      val result = MultiMap.create<PolyContextKind, PolyContextKindRules>()
      rulesPerKind.forEach { (kind, rules) ->
        val rulesPerName = rules.groupBy { it.name }
        val enablementRules = rulesPerName.mapValues { (_, entries) -> entries.mapNotNull { it.enablementRules } }
        val disablementRules = rulesPerName.mapValues { (_, entries) -> entries.mapNotNull { it.disablementRules } }
        result.putValue(kind, PolyContextKindRules.create(enablementRules, disablementRules))
      }
      result
    }
  }

  private fun createNameConversionRulesCache(): ClearableLazyValue<Map<FrameworkId, PolySymbolNameConversionRules>> =
    ClearableLazyValue.create {
      frameworkConfigs
        .asSequence()
        .mapNotNull { (webTypes, config) ->
          val framework = webTypes.framework ?: return@mapNotNull null
          val builder = PolySymbolNameConversionRules.builder()

          buildNameConverters(config.canonicalNames?.additionalProperties, { mergeConverters(listOf(it)) }, builder::addCanonicalNamesRule)
          buildNameConverters(config.matchNames?.additionalProperties, { mergeConverters(it) }, builder::addMatchNamesRule)
          buildNameConverters(config.nameVariants?.additionalProperties, { mergeConverters(it) }, builder::addCompletionVariantsRule)

          Pair(framework, builder.build())
        }
        .toMap()
    }

  protected class WebTypesJsonOriginImpl(
    webTypes: WebTypes,
    override val typeSupport: PolySymbolTypeSupport,
    private val project: Project,
    private val symbolLocationResolver: (source: SourceBase) -> WebTypesSymbol.Location? = { null },
    private val sourceSymbolResolver: (location: WebTypesSymbol.Location, cacheHolder: UserDataHolderEx) -> PsiElement? = { _, _ -> null },
    private val iconLoader: (path: String) -> Icon? = { null },
    override val version: String? = webTypes.version
  ) : WebTypesJsonOrigin {
    override val framework: FrameworkId? = webTypes.framework

    override val library: String? = webTypes.name

    private val contextExpr = webTypes.requiredContext ?: webTypes.context

    private val descriptionRenderer: (String) -> String =
      when (webTypes.descriptionMarkupWithLegacy) {
        WebTypes.DescriptionMarkup.HTML -> { doc -> doc }
        WebTypes.DescriptionMarkup.MARKDOWN -> { doc -> DocMarkdownToHtmlConverter.convert(project, doc) }
        else -> { doc -> "<p>" + StringUtil.escapeXmlEntities(doc).replace(EOL_PATTERN, "<br>") }
      }

    override val defaultIcon: Icon? = webTypes.defaultIcon?.let { IconLoader.createLazy { loadIcon(it) ?: EmptyIcon.ICON_0 } }

    override fun renderDescription(description: String): String = descriptionRenderer(description)

    override fun resolveSourceSymbol(source: SourceBase, cacheHolder: UserDataHolderEx): PsiElement? {
      return resolveSourceLocation(source)?.let { sourceSymbolResolver(it, cacheHolder) }
    }

    override fun resolveSourceLocation(source: SourceBase): WebTypesSymbol.Location? = symbolLocationResolver(source)

    override fun loadIcon(path: String): Icon? {
      return if (path.startsWith("<svg")) WebTypesSvgStringIconLoader.loadIcon(path) else iconLoader(path)
    }

    override fun matchContext(context: PolyContext): Boolean {
      return ((framework == null || context.framework == framework) && contextExpr?.evaluate(context) != false)
    }

    override fun toString(): String = "$library@$version ($framework)"
  }
}

private class WebTypesSymbolNameConversionRulesProvider(
  private val framework: FrameworkId,
  private val scope: WebTypesScopeBase,
  private val nameConversionRulesCache: ClearableLazyValue<Map<FrameworkId, PolySymbolNameConversionRules>>,
) : PolySymbolNameConversionRulesProvider {
  override fun getNameConversionRules(): PolySymbolNameConversionRules {
    return nameConversionRulesCache.value[framework] ?: PolySymbolNameConversionRules.empty()
  }

  override fun createPointer(): Pointer<out PolySymbolNameConversionRulesProvider> {
    val framework = framework
    val scopePtr = scope.createPointer()
    return Pointer {
      scopePtr.dereference()?.let {
        WebTypesSymbolNameConversionRulesProvider(
          framework = framework,
          scope = scope,
          nameConversionRulesCache = nameConversionRulesCache,
        )
      }
    }
  }

  override fun getModificationCount(): Long = scope.modificationCount
}

private val EOL_PATTERN: Regex = Regex("\n|\r\n")

private data class RulesEntry(
  val kind: PolyContextKind,
  val name: PolyContextName,
  val enablementRules: EnablementRules?,
  val disablementRules: DisablementRules?,
)