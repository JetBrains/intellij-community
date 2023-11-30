// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.metric.MetricInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class ResultPaths(val resourcePath: Path, val reportPath: Path)

data class ReferenceInfo(val pathToReport: Path, val metrics: List<MetricInfo>)

data class GeneratorDirectories(val filterDir: Path, val filesDir: Path, val resourcesDir: Path) {
  companion object {
    fun create(outputDir: String, filterName: String, comparisonFilterName: String, type: String = "html"): GeneratorDirectories {
      val filterDir: Path = Paths.get(outputDir, type, filterName, comparisonFilterName)
      val filesDir: Path = Paths.get(filterDir.toString(), "files")
      val resourcesDir: Path = Paths.get(filterDir.toString(), "res")

      listOf(filterDir, filesDir, resourcesDir).map { Files.createDirectories(it) }

      return GeneratorDirectories(filterDir, filesDir, resourcesDir)
    }
  }

  fun getPaths(fileName: String): ResultPaths {
    if (Files.exists(Paths.get(resourcesDir.toString(), "$fileName.js")))
      return getNextFilePaths(fileName)
    return ResultPaths(
      Paths.get(resourcesDir.toString(), "$fileName.js"),
      Paths.get(filesDir.toString(), "$fileName.html")
    )

  }

  private fun getNextFilePaths(fileName: String): ResultPaths {
    var index = 1
    do {
      index++
      val nextFile = Paths.get(resourcesDir.toString(), "$fileName-$index.js").toFile()
    }
    while (nextFile.exists())
    return ResultPaths(
      Paths.get(resourcesDir.toString(), "$fileName-$index.js"),
      Paths.get(filesDir.toString(), "$fileName-$index.html")
    )
  }
}
