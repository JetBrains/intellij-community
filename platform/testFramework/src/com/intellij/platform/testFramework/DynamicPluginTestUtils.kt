// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework

import com.intellij.ide.plugins.DynamicPlugins
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginDescriptorLoadingContext
import com.intellij.ide.plugins.PluginInitializationContext
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.loadDescriptorFromFileOrDirInTests
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.IndexingTestUtil
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files
import java.nio.file.Path

@JvmOverloads
fun loadAndInitDescriptorInTest(
  dir: Path,
  isBundled: Boolean = false,
  disabledPlugins: Set<String> = emptySet(),
): IdeaPluginDescriptorImpl {
  assertThat(dir).exists()
  PluginManagerCore.getAndClearPluginLoadingErrors()

  val buildNumber = BuildNumber.fromString("2042.42")!!
  val loadingContext = PluginDescriptorLoadingContext(
    getBuildNumberForDefaultDescriptorVersion = { buildNumber }
  )
  val initContext = PluginInitializationContext.buildForTest(
    essentialPlugins = emptySet(),
    disabledPlugins = disabledPlugins.mapTo(LinkedHashSet(), PluginId::getId),
    expiredPlugins = emptySet(),
    brokenPluginVersions = emptyMap(),
    getProductBuildNumber = { buildNumber },
    requirePlatformAliasDependencyForLegacyPlugins = false,
    checkEssentialPlugins = false,
    explicitPluginSubsetToLoad = null,
    disablePluginLoadingCompletely = false,
  )
  val result = loadDescriptorFromFileOrDirInTests(
    file = dir,
    loadingContext = loadingContext,
    isBundled = isBundled,
  )
  if (result == null) {
    assertThat(PluginManagerCore.getAndClearPluginLoadingErrors()).isNotEmpty()
    throw AssertionError("Cannot load plugin from $dir")
  }
  result.initialize(context = initContext)
  return result
}

@JvmOverloads
fun loadExtensionWithText(extensionTag: String, ns: String = "com.intellij"): Disposable {
  return loadPluginWithText(
    pluginBuilder = PluginBuilder.withModulesLang().extensions(extensionTag, ns),
    path = FileUtil.createTempDirectory("test", "test", true).toPath(),
  ).also {
    IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
  }
}

fun loadPluginWithText(
  pluginBuilder: PluginBuilder,
  path: Path,
  disabledPlugins: Set<String> = emptySet(),
): Disposable {
  val descriptor = loadAndInitDescriptorInTest(
    pluginBuilder = pluginBuilder,
    rootPath = path,
    disabledPlugins = disabledPlugins,
  )
  assertThat(DynamicPlugins.checkCanUnloadWithoutRestart(descriptor)).isNull()
  try {
    DynamicPlugins.loadPlugin(pluginDescriptor = descriptor)
    IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
  }
  catch (e: Exception) {
    unloadAndUninstallPlugin(descriptor)
    throw e
  }

  return Disposable {
    val reason = DynamicPlugins.checkCanUnloadWithoutRestart(descriptor)
    unloadAndUninstallPlugin(descriptor)
    assertThat(reason).isNull()
  }
}

fun loadAndInitDescriptorInTest(
  pluginBuilder: PluginBuilder,
  rootPath: Path,
  disabledPlugins: Set<String> = emptySet(),
  useTempDir: Boolean = false,
): IdeaPluginDescriptorImpl {
  val path = if (useTempDir)
    Files.createTempDirectory(rootPath, null)
  else
    rootPath

  val pluginDirectory = path.resolve("plugin")
  pluginBuilder.build(pluginDirectory)

  return loadAndInitDescriptorInTest(
    dir = pluginDirectory,
    disabledPlugins = disabledPlugins,
  )
}

fun setPluginClassLoaderForMainAndSubPlugins(rootDescriptor: IdeaPluginDescriptorImpl, classLoader: ClassLoader?) {
  rootDescriptor.pluginClassLoader = classLoader
  for (dependency in rootDescriptor.dependencies) {
    dependency.subDescriptor?.let {
      it.pluginClassLoader = classLoader
    }
  }
}

fun unloadAndUninstallPlugin(descriptor: IdeaPluginDescriptorImpl): Boolean {
  return DynamicPlugins.unloadPlugin(
    descriptor,
    DynamicPlugins.UnloadPluginOptions(disable = false),
  ).also {
    IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
  }
}