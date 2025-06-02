// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework

import com.intellij.ide.plugins.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.testFramework.plugins.*
import com.intellij.testFramework.IndexingTestUtil
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files
import java.nio.file.Path

internal val StubBuildNumber: BuildNumber get() = BuildNumber.fromString("2042.42")!!

internal val StubPluginDescriptorLoadingContext: PluginDescriptorLoadingContext get() = PluginDescriptorLoadingContext(
  getBuildNumberForDefaultDescriptorVersion = { StubBuildNumber }
)

@JvmOverloads
fun loadAndInitDescriptorInTest(
  dir: Path,
  isBundled: Boolean = false,
): PluginMainDescriptor {
  val result = loadDescriptorInTest(dir, isBundled)
  val initContext = PluginInitializationContext.buildForTest(
    essentialPlugins = emptySet(),
    disabledPlugins = emptySet(),
    expiredPlugins = emptySet(),
    brokenPluginVersions = emptyMap(),
    getProductBuildNumber = { StubBuildNumber },
    requirePlatformAliasDependencyForLegacyPlugins = false,
    checkEssentialPlugins = false,
    explicitPluginSubsetToLoad = null,
    disablePluginLoadingCompletely = false,
    currentProductModeId = ProductMode.MONOLITH.id,
  )
  result.initialize(context = initContext)
  return result
}

@JvmOverloads
fun loadDescriptorInTest(
  fileOrDir: Path,
  isBundled: Boolean = false,
  loadingContext: PluginDescriptorLoadingContext = StubPluginDescriptorLoadingContext
): PluginMainDescriptor {
  assertThat(fileOrDir).exists()
  PluginManagerCore.getAndClearPluginLoadingErrors()
  val result = loadDescriptorFromFileOrDirInTests(
    file = fileOrDir,
    loadingContext = loadingContext,
    isBundled = isBundled,
  )
  if (result == null) {
    assertThat(PluginManagerCore.getAndClearPluginLoadingErrors()).isNotEmpty()
    throw AssertionError("Cannot load plugin from $fileOrDir")
  }
  return result
}

@JvmOverloads
fun loadExtensionWithText(extensionTag: String, ns: String = "com.intellij"): Disposable {
  return loadPluginWithText(
    pluginSpec = plugin {
      dependsIntellijModulesLang()
      extensions(extensionTag, ns)
    },
    path = FileUtil.createTempDirectory("test", "test", true).toPath(),
  ).also {
    IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
  }
}

fun loadPluginWithText(
  pluginSpec: PluginSpec,
  path: Path,
): Disposable {
  val descriptor = loadAndInitDescriptorInTest(
    pluginSpec = pluginSpec,
    rootPath = path,
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
  )
}

fun loadAndInitDescriptorInTest(
  pluginSpec: PluginSpec,
  rootPath: Path,
  useTempDir: Boolean = false,
): IdeaPluginDescriptorImpl {
  val path = if (useTempDir) Files.createTempDirectory(rootPath, null) else rootPath
  val pluginDirectory = path.resolve("plugin")
  pluginSpec.buildDir(pluginDirectory)
  return loadAndInitDescriptorInTest(
    dir = pluginDirectory,
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