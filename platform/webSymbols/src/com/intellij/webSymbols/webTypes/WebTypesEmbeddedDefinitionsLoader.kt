// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.diagnostic.PluginException
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.util.text.SemVer
import com.intellij.webSymbols.webTypes.impl.WebTypesDefinitionsEP
import com.intellij.webSymbols.webTypes.json.WebTypes

@Service
class WebTypesEmbeddedDefinitionsLoader(val project: Project) : Disposable {

  companion object {

    fun getInstance(project: Project): WebTypesEmbeddedDefinitionsLoader = project.service()

    fun forPackage(project: Project,
                   packageName: String,
                   packageVersion: SemVer?): Pair<PluginDescriptor, WebTypes>? =
      getInstance(project).getWebTypes(packageName, packageVersion)

    fun getWebTypesEnabledPackages(project: Project): Set<String> =
      getInstance(project).webTypesEnabledPackages

    fun getPackagesEnabledByDefault(project: Project): Map<String, SemVer?> =
      getInstance(project).packagesEnabledByDefault

    fun getDefaultWebTypesScope(project: Project): WebTypesSymbolsScopeBase =
      getInstance(project).myDefaultWebTypesScope

    private val LOG = Logger.getInstance(WebTypesEmbeddedDefinitionsLoader::class.java)

  }

  private val state = ClearableLazyValue.create { State(project) }.also {
    WebTypesDefinitionsEP.EP_NAME.addChangeListener(Runnable { it.drop() }, this)
    WebTypesDefinitionsEP.EP_NAME_DEPRECATED.addChangeListener(Runnable { it.drop() }, this)
  }

  private val webTypesEnabledPackages: Set<String> = state.value.versionsRegistry.packages
  private val packagesEnabledByDefault: Map<String, SemVer> = state.value.packagesEnabledByDefault
  private val myDefaultWebTypesScope: WebTypesSymbolsScopeBase = state.value.myDefaultWebTypesScope

  private fun getWebTypes(packageName: String, packageVersion: SemVer?): Pair<PluginDescriptor, WebTypes>? =
    state.value.versionsRegistry.get(packageName, packageVersion)

  override fun dispose() {
  }

  private class State(private val project: Project) {
    val versionsRegistry = WebTypesVersionsRegistry<Pair<PluginDescriptor, WebTypes>>()
    val packagesEnabledByDefault: Map<String, SemVer>
    val myDefaultWebTypesScope: WebTypesSymbolsScopeBase

    init {
      val packagesEnabledByDefault = mutableMapOf<String, SemVer>()
      WebTypesDefinitionsEP.EP_NAME.extensions.plus(WebTypesDefinitionsEP.EP_NAME_DEPRECATED.extensions).forEach {
        try {
          val webTypes = it.instance
          val semVer = SemVer.parseFromText(webTypes.version)!!
          if (it.enableByDefault == true)
            packagesEnabledByDefault.merge(webTypes.name, semVer) { a, b ->
              if (a.isGreaterOrEqualThan(b)) a else b
            }
          versionsRegistry.put(webTypes.name, semVer, Pair(it.pluginDescriptor, webTypes))
        }
        catch (e: PluginException) {
          LOG.error(e)
        }
      }
      this.packagesEnabledByDefault = packagesEnabledByDefault
      myDefaultWebTypesScope = object : WebTypesSymbolsScopeBase() {
        init {
          packagesEnabledByDefault.forEach {
            versionsRegistry.get(it.key, it.value)?.let { (pluginDescriptor, webTypes) ->
              addWebTypes(webTypes, WebTypesJsonOriginImpl(
                webTypes,
                typeSupport = WebTypesSymbolTypeSupport.get(webTypes),
                iconLoader = WebTypesEmbeddedIconLoader(pluginDescriptor)::loadIcon,
                version = null
              ))
            }
          }
        }

        override fun createPointer(): Pointer<out WebTypesSymbolsScopeBase> {
          val project = this@State.project
          return Pointer<WebTypesSymbolsScopeBase> {
            if (project.isDisposed) return@Pointer null
            getInstance(project).state.value.myDefaultWebTypesScope
          }
        }
      }
    }
  }

}