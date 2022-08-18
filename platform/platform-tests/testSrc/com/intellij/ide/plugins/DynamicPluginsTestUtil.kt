// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DynamicPluginsTestUtil")
@file:Suppress("UsePropertyAccessSyntax")
package com.intellij.ide.plugins

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

@JvmOverloads
internal fun loadDescriptorInTest(
  dir: Path,
  isBundled: Boolean = false,
  disabledPlugins: Set<String> = emptySet(),
): IdeaPluginDescriptorImpl {
  assertThat(dir).exists()
  PluginManagerCore.getAndClearPluginLoadingErrors()

  val buildNumber = BuildNumber.fromString("2042.42")!!
  val result = runBlocking {
    loadDescriptorFromFileOrDir(
      file = dir,
      context = DescriptorListLoadingContext(
        brokenPluginVersions = emptyMap(),
        productBuildNumber = { buildNumber },
        disabledPlugins = disabledPlugins.mapTo(LinkedHashSet(), PluginId::getId),
      ),
      pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
      isBundled = isBundled,
      isEssential = true,
      isDirectory = Files.isDirectory(dir),
      useCoreClassLoader = false,
      isUnitTestMode = true,
      pool = null,
    )
  }

  if (result == null) {
    assertThat(PluginManagerCore.getAndClearPluginLoadingErrors()).isNotEmpty()
    throw AssertionError("Cannot load plugin from $dir")
  }

  return result
}

@JvmOverloads
internal fun createPluginLoadingResult(checkModuleDependencies: Boolean = false): PluginLoadingResult {
  return PluginLoadingResult(checkModuleDependencies = checkModuleDependencies)
}

@JvmOverloads
fun loadExtensionWithText(extensionTag: String, ns: String = "com.intellij"): Disposable {
  return loadPluginWithText(
    pluginBuilder = PluginBuilder().extensions(extensionTag, ns),
    path = FileUtil.createTempDirectory("test", "test", true).toPath(),
  )
}

internal fun loadPluginWithText(
  pluginBuilder: PluginBuilder,
  path: Path,
  disabledPlugins: Set<String> = emptySet(),
): Disposable {
  val descriptor = loadDescriptorInTest(
    pluginBuilder = pluginBuilder,
    rootPath = path,
    disabledPlugins = disabledPlugins,
  )
  assertThat(DynamicPlugins.checkCanUnloadWithoutRestart(descriptor)).isNull()
  try {
    DynamicPlugins.loadPlugin(pluginDescriptor = descriptor)
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

internal fun loadDescriptorInTest(
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

  return loadDescriptorInTest(
    dir = pluginDirectory,
    disabledPlugins = disabledPlugins,
  )
}

internal fun setPluginClassLoaderForMainAndSubPlugins(rootDescriptor: IdeaPluginDescriptorImpl, classLoader: ClassLoader?) {
  rootDescriptor.pluginClassLoader = classLoader
  for (dependency in rootDescriptor.pluginDependencies) {
    dependency.subDescriptor?.let {
      it.pluginClassLoader = classLoader
    }
  }
}

internal fun unloadAndUninstallPlugin(descriptor: IdeaPluginDescriptorImpl): Boolean {
  return DynamicPlugins.unloadPlugin(
    descriptor,
    DynamicPlugins.UnloadPluginOptions(disable = false),
  )
}