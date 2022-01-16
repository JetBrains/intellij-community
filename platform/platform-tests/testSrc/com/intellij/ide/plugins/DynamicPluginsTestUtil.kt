// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("DynamicPluginsTestUtil")
@file:Suppress("UsePropertyAccessSyntax")
package com.intellij.ide.plugins

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.Ksuid
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier

internal fun loadDescriptorInTest(dir: Path,
                                  disabledPlugins: Set<PluginId> = emptySet(),
                                  isBundled: Boolean = false): IdeaPluginDescriptorImpl {
  assertThat(dir).exists()
  PluginManagerCore.getAndClearPluginLoadingErrors()
  val buildNumber = BuildNumber.fromString("2042.42")!!
  val parentContext = DescriptorListLoadingContext(disabledPlugins = disabledPlugins,
                                                   result = PluginLoadingResult(emptyMap(), Supplier { buildNumber }))
  val result = loadDescriptorFromFileOrDir(file = dir,
                                           context = parentContext,
                                           pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                           isBundled = isBundled,
                                           isEssential = true,
                                           isDirectory = Files.isDirectory(dir),
                                           useCoreClassLoader = false)
  if (result == null) {
    @Suppress("USELESS_CAST")
    assertThat(PluginManagerCore.getAndClearPluginLoadingErrors()).isNotEmpty()
    throw AssertionError("Cannot load plugin from $dir")
  }

  result.jarFiles = emptyList()
  return result
}

@JvmOverloads
fun loadExtensionWithText(extensionTag: String, ns: String = "com.intellij"): Disposable {
  val builder = PluginBuilder().extensions(extensionTag, ns)
  return loadPluginWithText(builder, FileSystems.getDefault())
}

internal fun loadPluginWithText(pluginBuilder: PluginBuilder, fs: FileSystem): Disposable {
  val directory = if (fs == FileSystems.getDefault()) {
    FileUtil.createTempDirectory("test", "test", true).toPath()
  }
  else {
    fs.getPath("/").resolve(Ksuid.generate())
  }

  val descriptor = loadDescriptorInTest(
    pluginBuilder,
    directory,
  )
  assertThat(DynamicPlugins.checkCanUnloadWithoutRestart(descriptor)).isNull()
  try {
    DynamicPlugins.loadPlugin(pluginDescriptor = descriptor)
  }
  catch (e: Exception) {
    DynamicPlugins.unloadAndUninstallPlugin(descriptor)
    throw e
  }

  return Disposable {
    val reason = DynamicPlugins.checkCanUnloadWithoutRestart(descriptor)
    DynamicPlugins.unloadAndUninstallPlugin(descriptor)
    assertThat(reason).isNull()
  }
}

internal fun loadDescriptorInTest(
  pluginBuilder: PluginBuilder,
  directory: Path,
): IdeaPluginDescriptorImpl {
  val pluginDirectory = directory.resolve("plugin")
  pluginBuilder.build(pluginDirectory)
  return loadDescriptorInTest(pluginDirectory)
}

internal fun setPluginClassLoaderForMainAndSubPlugins(rootDescriptor: IdeaPluginDescriptorImpl, classLoader: ClassLoader?) {
  rootDescriptor.pluginClassLoader = classLoader
  for (dependency in rootDescriptor.pluginDependencies) {
    dependency.subDescriptor?.let {
      it.pluginClassLoader = classLoader
    }
  }
}