// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang.test

import com.intellij.openapi.application.PathManager
import com.intellij.project.IntelliJProjectConfiguration
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.junit.Assert
import org.junit.Test
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

class UrlClassLoaderSplitPackageTest {
  @Test
  fun `no expected classes in com_intellij_util_lang package`() {
    val urlClassLoaderPackages = listOf(
      "com.intellij.util.lang",
      "org.jetbrains.ikv",
    )
    val platformLoaderModules = setOf(
      "intellij.platform.util.rt.java8",
      "intellij.platform.util.classLoader",
      "intellij.platform.util.zip",
      "intellij.platform.boot",
    )
    val knownClassesFromOtherModules = setOf(
      "CompoundRuntimeException",
      "JavaVersion",
      "JavaVersion\$Companion",
    )
    val project = IntelliJProjectConfiguration.loadIntelliJProject(PathManager.getHomePath())
    project.modules.filterNot { it.name in platformLoaderModules }.forEach { module -> 
      val moduleOutput = JpsJavaExtensionService.getInstance().getOutputDirectoryPath(module, false)!!
      urlClassLoaderPackages.forEach { packageName ->
        val packageDir = moduleOutput.resolve(packageName.replace('.', '/'))
        if (packageDir.exists()) {
          val classNames = packageDir.listDirectoryEntries("*.class").map { it.fileName.toString().removeSuffix(".class") }
          val incorrectClasses = classNames - knownClassesFromOtherModules
          Assert.assertTrue("""
              |Module '${module.name}' defines classes (${incorrectClasses.joinToString()}) in '$packageName' which is treated in a special way by UrlClassLoader::findClass:
              |Move these classes to a different package to avoid problems (see IDEA-331043 for details).
            """.trimMargin(), incorrectClasses.isEmpty())
        }
      }
    }
  }
}