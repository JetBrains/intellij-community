// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.testFramework

import com.intellij.platform.plugins.parser.impl.XIncludeLoader
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.nio.file.Path
import kotlin.io.path.*

class LoadFromSourceXIncludeLoader(
  private val prefixesOfPathsIncludedFromLibrariesViaXiInclude: List<String>,
  private val project: JpsProject,
  private val parentDirectoriesPatterns: List<String>,
) : XIncludeLoader {
  private val shortXmlPathToFullPaths = collectXmlFiles()

  private fun collectXmlFiles(): Map<String, List<Path>> {
    val shortNameToPaths = LinkedHashMap<String, MutableList<Path>>()
    for (module in project.modules) {
      for (sourceRoot in module.getSourceRoots(JavaResourceRootType.RESOURCE)) {
        for (directoryPattern in parentDirectoriesPatterns) {
          val (directoryName, withChildren) = if (directoryPattern.endsWith("/*")) {
            directoryPattern.removeSuffix("/*") to true
          }
          else {
            directoryPattern to false
          }
          val rootDirectory = if (directoryName == "") sourceRoot.path else sourceRoot.path.resolve(directoryName)
          if (rootDirectory.isDirectory()) {
            val rootPrefix = if (directoryName == "") "" else "$directoryName/"
            val directoriesWithPrefixes = if (withChildren) {
              rootDirectory.listDirectoryEntries().filter { it.isDirectory() }.map { it to "$rootPrefix${it.name}/" }
            }
            else {
              listOf(rootDirectory to rootPrefix)
            }
            for ((directory, prefix) in directoriesWithPrefixes) {
              for (xmlFile in directory.listDirectoryEntries("*.xml")) {
                val shortPath = "$prefix${xmlFile.name}"
                if (shortPath == "META-INF/plugin.xml") {
                  continue
                }
                shortNameToPaths.computeIfAbsent(shortPath) { ArrayList() }.add(xmlFile)
              }
            }
          }
        }
      }
    }
    return shortNameToPaths
  }

  override fun loadXIncludeReference(path: String): XIncludeLoader.LoadedXIncludeReference? {
    if (prefixesOfPathsIncludedFromLibrariesViaXiInclude.any { path.startsWith(it) }) {
      //todo: support loading from libraries
      return XIncludeLoader.LoadedXIncludeReference("<idea-plugin/>".byteInputStream(), "dummy tag for external $path")
    }
    val directoryName = path.substringBeforeLast(delimiter = '/', missingDelimiterValue = "")
    val parentDirectoryName = directoryName.substringBeforeLast('/', missingDelimiterValue = "")
    if (parentDirectoriesPatterns.none { it == directoryName || it.removeSuffix("/*") == parentDirectoryName }) {
      error("Path $path is referenced in xi:include, but it's parent directory is not specified in the list of directories where files are resolved: $parentDirectoriesPatterns")
    }
    val files = shortXmlPathToFullPaths[path] ?: emptyList()
    val file = files.firstOrNull()
    if (file != null) {
      return XIncludeLoader.LoadedXIncludeReference(file.inputStream(), file.pathString)
    }
    return null
  }
}