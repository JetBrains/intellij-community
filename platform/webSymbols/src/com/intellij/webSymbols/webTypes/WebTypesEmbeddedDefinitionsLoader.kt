// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.diagnostic.PluginException
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.util.text.SemVer
import com.intellij.webSymbols.webTypes.impl.WebTypesDefinitionsEP
import com.intellij.webSymbols.webTypes.json.WebTypes

@Service(Service.Level.PROJECT)
class WebTypesEmbeddedDefinitionsLoader(private val project: Project) : Disposable {
  companion object {
    fun getInstance(project: Project): WebTypesEmbeddedDefinitionsLoader = project.service()

    fun forPackage(project: Project,
                   packageName: String,
                   packageVersion: SemVer?): Pair<PluginDescriptor, WebTypes>? {
      return getInstance(project).getWebTypes(packageName, packageVersion)
    }

    fun getWebTypesEnabledPackages(project: Project): Set<String> = getInstance(project).webTypesEnabledPackages

    fun getPackagesEnabledByDefault(project: Project): Map<String, SemVer?> = getInstance(project).packagesEnabledByDefault

    fun getDefaultWebTypesScope(project: Project): WebTypesScopeBase = getInstance(project).defaultWebTypesScope

    private val LOG = logger<WebTypesEmbeddedDefinitionsLoader>()
  }

  private val state = ClearableLazyValue.create { State(project) }.also {
    WebTypesDefinitionsEP.EP_NAME.addChangeListener(Runnable { it.drop() }, this)
    WebTypesDefinitionsEP.EP_NAME_DEPRECATED.addChangeListener(Runnable { it.drop() }, this)
  }

  private val webTypesEnabledPackages: Set<String> get() = state.value.versionsRegistry.packages
  private val packagesEnabledByDefault: Map<String, SemVer> get() = state.value.packagesEnabledByDefault
  private val defaultWebTypesScope: WebTypesScopeBase get() = state.value.defaultWebTypesScope

  private fun getWebTypes(packageName: String, packageVersion: SemVer?): Pair<PluginDescriptor, WebTypes>? {
    return state.value.versionsRegistry.get(packageName, packageVersion)
  }

  override fun dispose() {
  }

  private class State(val project: Project) {
    val versionsRegistry = WebTypesVersionsRegistry<Pair<PluginDescriptor, WebTypes>>()
    val packagesEnabledByDefault: Map<String, SemVer>
    val defaultWebTypesScope: WebTypesScopeBase

    init {
      val packagesEnabledByDefault = HashMap<String, SemVer>()
      for (ep in WebTypesDefinitionsEP.EP_NAME.extensionList.plus(WebTypesDefinitionsEP.EP_NAME_DEPRECATED.extensionList)) {
        try {
          val webTypes = ep.instance
          val semVer = SemVer.parseFromText(webTypes.version)!!
          if (ep.enableByDefault == true) {
            packagesEnabledByDefault.merge(webTypes.name, semVer) { a, b ->
              if (a.isGreaterOrEqualThan(b)) a else b
            }
          }
          versionsRegistry.put(webTypes.name, semVer, Pair(ep.pluginDescriptor, webTypes))
        }
        catch (e: PluginException) {
          LOG.error(e)
        }
      }
      this.packagesEnabledByDefault = packagesEnabledByDefault
      defaultWebTypesScope = DefaultWebTypesScope(this, project)
    }

    override fun equals(other: Any?): Boolean {
      return other is State
             && project == other.project
             && versionsRegistry == other.versionsRegistry
             && packagesEnabledByDefault == other.packagesEnabledByDefault
    }

    override fun hashCode(): Int {
      return project.hashCode()
    }

    override fun toString(): String {
      return "WebTypesEmbeddedDefinitionsLoaderState: project=$project; versionsRegistry=$versionsRegistry; packagesEnabledByDefault=$packagesEnabledByDefault"
    }

  }

  private class DefaultWebTypesScope(private val state: State, private val project: Project) : WebTypesScopeBase() {
    init {
      for (entry in state.packagesEnabledByDefault) {
        state.versionsRegistry.get(entry.key, entry.value)?.let { (pluginDescriptor, webTypes) ->
          addWebTypes(webTypes, WebTypesJsonOriginImpl(
            webTypes = webTypes,
            typeSupport = WebTypesSymbolTypeSupportFactory.get(webTypes),
            project = project,
            iconLoader = WebTypesEmbeddedIconLoader(pluginDescriptor)::loadIcon,
            version = null
          ))
        }
      }
    }

    override fun equals(other: Any?): Boolean {
      return other is DefaultWebTypesScope
             && other.state == state
    }

    override fun hashCode(): Int {
      return state.hashCode()
    }

    override fun createPointer(): Pointer<out WebTypesScopeBase> {
      val project = state.project
      return Pointer<WebTypesScopeBase> {
        if (project.isDisposed) {
          return@Pointer null
        }
        getInstance(project).state.value.defaultWebTypesScope
      }
    }
  }
}