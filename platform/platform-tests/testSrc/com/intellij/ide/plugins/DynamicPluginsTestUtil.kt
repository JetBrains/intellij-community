// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.DynamicPlugins.loadPlugin
import com.intellij.ide.plugins.DynamicPlugins.unloadPlugin
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.assertions.Assertions
import java.io.File
import java.lang.AssertionError
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
  val pair = preparePluginDescriptor(pluginXml)
  val plugin = pair.first
  var descriptor = pair.second
  Assertions.assertThat(DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor)).isTrue()
  descriptor.setLoader(loader)
  try {
    loadPlugin(descriptor, false)
  }
  catch (e: Exception) {
    unloadPlugin(descriptor, false)
    throw RuntimeException(e)
  }

  return Disposable {
    descriptor = loadDescriptorInTest(plugin.toPath().parent.parent)
    val canBeUnloaded = DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor)
    unloadPlugin(descriptor, false)
    
    if (!canBeUnloaded) {
      throw AssertionError("'Allow unload without restart' returned false, why expected true")
    }
  }
}

fun preparePluginDescriptor(pluginXml: String): Pair<File, IdeaPluginDescriptorImpl> {
  val directory = FileUtil.createTempDirectory("test", "test", true)
  val plugin = File(directory, "/plugin/META-INF/plugin.xml")
  FileUtil.createParentDirs(plugin)
  FileUtil.writeToFile(plugin, pluginXml)
  val descriptor = loadDescriptorInTest(plugin.toPath().parent.parent)
  return Pair(plugin, descriptor)
}