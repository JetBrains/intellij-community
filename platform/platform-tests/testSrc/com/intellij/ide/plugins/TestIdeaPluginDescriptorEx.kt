// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.plugins.parser.impl.elements.ActionElement
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.*

abstract class TestIdeaPluginDescriptorEx : IdeaPluginDescriptorEx {
  override val moduleLoadingRule: ModuleLoadingRule?
    get() = throw AssertionError("unexpected call")
  override val incompatiblePlugins: List<PluginId>
    get() = throw AssertionError("unexpected call")
  override val pluginAliases: List<PluginId>
    get() = throw AssertionError("unexpected call")
  override val packagePrefix: String?
    get() = throw AssertionError("unexpected call")
  override val actions: List<ActionElement>
    get() = throw AssertionError("unexpected call")
  override val appContainerDescriptor: ContainerDescriptor
    get() = throw AssertionError("unexpected call")
  override val projectContainerDescriptor: ContainerDescriptor
    get() = throw AssertionError("unexpected call")
  override val moduleContainerDescriptor: ContainerDescriptor
    get() = throw AssertionError("unexpected call")
  override val extensions: Map<String, List<ExtensionDescriptor>>
    get() = throw AssertionError("unexpected call")
  override val useCoreClassLoader: Boolean
    get() = throw AssertionError("unexpected call")
  override val isUseIdeaClassLoader: Boolean
    get() = throw AssertionError("unexpected call")
  override val isIndependentFromCoreClassLoader: Boolean
    get() = throw AssertionError("unexpected call")
  override var isMarkedForLoading: Boolean
    get() = throw AssertionError("unexpected call")
    set(value) { throw AssertionError("unexpected call") }
  override val moduleName: String?
    get() = throw AssertionError("unexpected call")
  override val moduleDependencies: ModuleDependencies
    get() = throw AssertionError("unexpected call")

  override fun getDependencies(): List<IdeaPluginDependency> {
    throw AssertionError("unexpected call")
  }

  override fun getDescriptorPath(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getPluginId(): PluginId {
    throw AssertionError("unexpected call")
  }

  override fun getPluginClassLoader(): ClassLoader? {
    throw AssertionError("unexpected call")
  }

  override fun getPluginPath(): Path? {
    throw AssertionError("unexpected call")
  }

  override fun getDescription(): @Nls String? {
    throw AssertionError("unexpected call")
  }

  override fun getChangeNotes(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getName(): @NlsSafe String? {
    throw AssertionError("unexpected call")
  }

  override fun getProductCode(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getReleaseDate(): Date? {
    throw AssertionError("unexpected call")
  }

  override fun getReleaseVersion(): Int {
    throw AssertionError("unexpected call")
  }

  override fun isLicenseOptional(): Boolean {
    throw AssertionError("unexpected call")
  }

  override fun getVendor(): @NlsSafe String? {
    throw AssertionError("unexpected call")
  }

  override fun getVersion(): @NlsSafe String? {
    throw AssertionError("unexpected call")
  }

  override fun getResourceBundleBaseName(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getCategory(): @NlsSafe String? {
    throw AssertionError("unexpected call")
  }

  override fun getVendorEmail(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getVendorUrl(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getUrl(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getSinceBuild(): @NlsSafe String? {
    throw AssertionError("unexpected call")
  }

  override fun getUntilBuild(): @NlsSafe String? {
    throw AssertionError("unexpected call")
  }

  @Deprecated("Deprecated in Java")
  override fun isEnabled(): Boolean {
    throw AssertionError("unexpected call")
  }
  
  @Deprecated("Deprecated in Java")
  override fun setEnabled(enabled: Boolean) {
    throw AssertionError("unexpected call")
  }
}