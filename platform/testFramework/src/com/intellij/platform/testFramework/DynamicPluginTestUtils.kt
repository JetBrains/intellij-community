// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework

import com.intellij.ide.plugins.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.plugins.*
import com.intellij.testFramework.IndexingTestUtil
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path

internal val StubBuildNumber: BuildNumber get() = BuildNumber.fromString("2042.42")!!

internal val StubPluginDescriptorLoadingContext: PluginDescriptorLoadingContext get() = PluginDescriptorLoadingContext(
  getBuildNumberForDefaultDescriptorVersion = { StubBuildNumber }
)

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
    pluginsDir = FileUtil.createTempDirectory("test", "test", true).toPath(),
  ).also {
    IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
  }
}

fun loadPluginWithText(
  pluginSpec: PluginSpec,
  pluginsDir: Path,
): Disposable {
  val descriptor = loadDescriptorInTest(
    pluginSpec = pluginSpec,
    pluginsDir = pluginsDir,
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
    invokeAndWaitIfNeeded {
      unloadAndUninstallPlugin(descriptor)
    }
    assertThat(reason).isNull()
  }
}

fun loadDescriptorInTest(
  pluginSpec: PluginSpec,
  pluginsDir: Path
): PluginMainDescriptor {
  val path = pluginSpec.buildDistribution(pluginsDir)
  return loadDescriptorInTest(fileOrDir = path)
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