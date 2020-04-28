// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")
package com.intellij.ide.plugins

import com.intellij.ide.plugins.DynamicPlugins.loadPlugin
import com.intellij.ide.plugins.DynamicPlugins.unloadPlugin
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.write
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.math.abs

internal fun loadDescriptorInTest(dir: Path, disabledPlugins: Set<PluginId> = emptySet(), isBundled: Boolean = false): IdeaPluginDescriptorImpl {
  assertThat(dir).exists()
  PluginManagerCore.ourPluginError = null
  val buildNumber = BuildNumber.fromString("2042.42")
  val parentContext = DescriptorListLoadingContext(0, disabledPlugins, PluginLoadingResult(emptyMap(), Supplier { buildNumber }))
  val result = DescriptorLoadingContext(parentContext, isBundled, /* isEssential = */ true,
                                        PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER).use { context ->
    PluginManagerCore.loadDescriptorFromFileOrDir(dir, PluginManagerCore.PLUGIN_XML, context, Files.isDirectory(dir))
  }
  if (result == null) {
    @Suppress("USELESS_CAST")
    assertThat(PluginManagerCore.ourPluginError as String?).isNotNull
    PluginManagerCore.ourPluginError = null
  }
  return result!!
}

@JvmOverloads
fun loadExtensionWithText(
  extensionTag: String,
  loader: ClassLoader = DynamicPlugins::class.java.classLoader,
  ns: String = "com.intellij"
): Disposable {
  val name = "test" + abs(extensionTag.hashCode())
  val text = """<idea-plugin>
  <name>$name</name>
  <extensions defaultExtensionNs="$ns">
    $extensionTag
  </extensions>
</idea-plugin>"""

  return loadPluginWithText(text, loader, FileSystems.getDefault())
}

internal fun loadPluginWithText(pluginXml: String, loader: ClassLoader, fs: FileSystem): Disposable {
  val pair = preparePluginDescriptor(pluginXml, fs)
  val plugin = pair.first
  var descriptor = pair.second
  assertThat(DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor)).isTrue()
  descriptor.setLoader(loader)
  try {
    loadPlugin(descriptor)
  }
  catch (e: Exception) {
    unloadPlugin(descriptor, false)
    throw e
  }

  return Disposable {
    descriptor = loadDescriptorInTest(plugin.parent.parent)
    val canBeUnloaded = DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor)
    unloadPlugin(descriptor, false)

    assertThat(canBeUnloaded).isTrue()
  }
}

private fun preparePluginDescriptor(pluginXml: String, fs: FileSystem): Pair<Path, IdeaPluginDescriptorImpl> {
  val directory = if (fs == FileSystems.getDefault()) FileUtil.createTempDirectory("test", "test", true).toPath() else Files.createTempDirectory(fs.getPath("/"), null)
  val plugin = directory.resolve("plugin/META-INF/plugin.xml")
  plugin.write(pluginXml)
  val descriptor = loadDescriptorInTest(plugin.parent.parent)
  return Pair(plugin, descriptor)
}