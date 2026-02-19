// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application

import com.intellij.openapi.application.PathManager
import com.intellij.project.IntelliJProjectConfiguration
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.nio.file.Files
import java.nio.file.Path

object JitWatchConfigGenerator {
  @JvmStatic
  fun main(args: Array<String>) {
    val classes = Files.newDirectoryStream(Path.of("out/classes/production").toAbsolutePath()).use { directoryStream ->
      directoryStream.mapNotNull { file ->
        val moduleName = file.fileName.toString()
        if (isExcludedModule(moduleName)) {
          null
        }
        else {
          file.toString()
        }
      }
    }

    val project: JpsProject by lazy { IntelliJProjectConfiguration.loadIntelliJProject(PathManager.getHomePath()) }
    val sources = project.modules.asSequence()
      .filter { !isExcludedModule(it.name) }
      .flatMap { module ->
        module.sourceRoots.asSequence()
          .filter { it.rootType == JavaSourceRootType.SOURCE }
          .map { it.path }
      }

    println(
      "Classes: " + classes.joinToString(",") +
      "\n" +
      "Sources: " + sources.joinToString(",")
    )
  }
}

private fun isExcludedModule(fileName: String): Boolean {
  return fileName.contains("fleet") ||
         fileName.startsWith("intellij.resharper.") ||
         fileName.startsWith("intellij.rider.") ||
         fileName.startsWith("intellij.php.") ||
         fileName.startsWith("intellij.pycharm.") ||
         fileName.startsWith("intellij.javaee.") ||
         fileName.contains("android")
}
