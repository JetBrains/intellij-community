// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.DynamicPlugins.loadPlugin
import com.intellij.ide.plugins.DynamicPlugins.unloadPlugin
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.assertions.Assertions
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

fun loadDescriptorInTest(dir: Path): IdeaPluginDescriptorImpl {
  Assertions.assertThat(dir).exists()
  PluginManagerCore.ourPluginError = null
  val parentContext = DescriptorListLoadingContext.createSingleDescriptorContext(emptySet())
  val result = DescriptorLoadingContext(parentContext, /* isBundled = */ false, /* isEssential = */ true,
                                        PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER).use { context ->
    PluginManagerCore.loadDescriptorFromFileOrDir(dir, PluginManagerCore.PLUGIN_XML, context, Files.isDirectory(dir))
  }
  if (result == null) {
    @Suppress("USELESS_CAST")
    Assertions.assertThat(PluginManagerCore.ourPluginError as String?).isNotNull
    PluginManagerCore.ourPluginError = null
  }
  return result!!
}

@JvmOverloads
fun loadExtensionWithText(extensionTag: String, loader: ClassLoader, ns: String = "com.intellij"): Disposable {
  val name = "test" + abs(extensionTag.hashCode())
  val text = """<idea-plugin>
  <name>$name</name>
  <extensions defaultExtensionNs="$ns">
    $extensionTag
  </extensions>
</idea-plugin>"""

  return loadPluginWithText(text, loader)
}

fun loadPluginWithText(pluginXml: String, loader: ClassLoader): Disposable {
  val directory = FileUtil.createTempDirectory("test", "test", true)
  val plugin = File(directory, "/plugin/META-INF/plugin.xml")
  FileUtil.createParentDirs(plugin)
  FileUtil.writeToFile(plugin, pluginXml)
  var descriptor = loadDescriptorInTest(plugin.toPath().parent.parent)
  descriptor.setLoader(loader)
  loadPlugin(descriptor, false)

  return Disposable {
    descriptor = loadDescriptorInTest(plugin.toPath().parent.parent)
    unloadPlugin(descriptor, false)
  }
}