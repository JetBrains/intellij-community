// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.testFramework

import com.intellij.platform.pluginSystem.parser.impl.LoadedXIncludeReference
import com.intellij.platform.pluginSystem.parser.impl.XIncludeLoader
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString

class LoadFromSourceXIncludeLoaderFactory(
  private val xiIncludeTargetsResidingInLibraries: List<String>,
  private val project: JpsProject,
  private val parentDirectoriesPatterns: List<String>,
) {
  private val shortXmlPathToLocation = collectXmlFiles()
  private val resolvedXIncludeDirectivesSet = LinkedHashSet<ResolvedXIncludeDirective>()
  val resolvedXIncludeDirectives: List<ResolvedXIncludeDirective>
    get() = resolvedXIncludeDirectivesSet.toList()

  private fun collectXmlFiles(): Map<String, List<XmlFileLocationInModule>> {
    val shortNameToLocations = LinkedHashMap<String, MutableList<XmlFileLocationInModule>>()
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
                shortNameToLocations.computeIfAbsent(shortPath) { ArrayList() }.add(XmlFileLocationInModule(module, xmlFile))
              }
            }
          }
        }
      }
    }
    return shortNameToLocations
  }

  fun createLoader(sourceFile: Path, sourceModule: JpsModule): XIncludeLoader {
    return LoadFromSourceXIncludeLoader(sourceFile, sourceModule)
  }

  inner class LoadFromSourceXIncludeLoader(private val sourceFile: Path, private val sourceModule: JpsModule) : XIncludeLoader {
    override fun loadXIncludeReference(path: String): LoadedXIncludeReference? {
      if (isLibraryXiIncludeTarget(path, xiIncludeTargetsResidingInLibraries)) {
        return LoadedXIncludeReference("<idea-plugin/>".encodeToByteArray(), "dummy tag for external $path")
      }
      val directoryName = path.substringBeforeLast(delimiter = '/', missingDelimiterValue = "")
      val parentDirectoryName = directoryName.substringBeforeLast('/', missingDelimiterValue = "")
      if (parentDirectoriesPatterns.none { it == directoryName || it.removeSuffix("/*") == parentDirectoryName }) {
        error("Path $path is referenced in xi:include, but its parent directory is not specified in the list of directories where files are resolved: $parentDirectoriesPatterns")
      }
      val locations = shortXmlPathToLocation[path] ?: emptyList()
      val location = locations.firstOrNull()
      if (location != null) {
        resolvedXIncludeDirectivesSet.add(ResolvedXIncludeDirective(sourceModule, location.module, sourceFile, location.path))
        return LoadedXIncludeReference(Files.readAllBytes(location.path), location.path.pathString)
      }
      return null
    }
  }
}

private data class XmlFileLocationInModule(val module: JpsModule, val path: Path)