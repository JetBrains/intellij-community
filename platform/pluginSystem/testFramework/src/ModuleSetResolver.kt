// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.testFramework

import com.intellij.openapi.util.JDOMUtil
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

class MissingModuleSetDescriptorException(
  val moduleSetName: String,
  val descriptorFileName: String,
  val communityPath: Path,
  val licenseCommonPath: Path,
) : IllegalStateException(
  "Cannot resolve module set '$moduleSetName': generated descriptor '$descriptorFileName' " +
  "was not found in '$communityPath' or '$licenseCommonPath'."
)

fun MissingModuleSetDescriptorException.buildStalePackagingDataMessage(
  contentYamlPath: Path,
  projectRoot: Path,
  generatorTestName: String,
): String {
  val displayYamlPath = contentYamlPath.toDisplayPath(projectRoot)
  return buildString {
    appendLine("Cannot resolve module set '$moduleSetName' referenced from '$displayYamlPath'.")
    appendLine(
      "The generated descriptor '$descriptorFileName' is missing, so the packaging data is out of date."
    )
    appendLine("Run '$generatorTestName' to regenerate the packaging data, then re-run this test.")
    appendLine("Checked descriptor paths:")
    appendLine("  ${communityPath.toDisplayPath(projectRoot)}")
    appendLine("  ${licenseCommonPath.toDisplayPath(projectRoot)}")
  }.trimEnd()
}

private data class ModuleSetDescriptorPaths(
  val communityPath: Path,
  val licenseCommonPath: Path,
)

/**
 * Checks if the path refers to a module set file.
 * Handles both absolute (/META-INF/...) and relative (META-INF/...) formats.
 */
fun isModuleSetPath(path: String): Boolean = path.removePrefix("/").startsWith("META-INF/intellij.moduleSets.")

/**
 * Resolves the path to a module set XML file, checking community first, then licenseCommon.
 * @param fileName the XML filename (e.g., "intellij.moduleSets.essential.xml")
 * @param projectRoot the project root directory
 * @return Path to the module set file
 */
fun resolveModuleSetPath(fileName: String, projectRoot: Path): Path {
  val descriptorPaths = getModuleSetDescriptorPaths(fileName, projectRoot)
  if (descriptorPaths.communityPath.exists()) {
    return descriptorPaths.communityPath
  }
  else {
    return descriptorPaths.licenseCommonPath
  }
}

/**
 * Resolves a module set name into a list of actual module names, recursively following x-include references.
 *
 * If the moduleName starts with "intellij.moduleSets.", attempts to find and parse the corresponding XML file
 * in community/platform/platform-resources/generated/META-INF/ or ultimate/platform-ultimate/resources/META-INF/,
 * then recursively resolves any nested module sets referenced via x-include.
 *
 * @param moduleSetName the name like "intellij.moduleSets.debugger.streams"
 * @param embeddedOnly if true, only include modules registered with `loading="embedded"`
 * @param ultimateRoot the root directory of the ultimate repository
 * @return list of actual module names from the module set XML and all nested module sets, or singleton list with original name if not a module set
 */
fun resolveModuleSet(moduleSetName: String, embeddedOnly: Boolean, ultimateRoot: Path): List<String> {
  if (!moduleSetName.startsWith("intellij.moduleSets.")) {
    return listOf(moduleSetName)
  }

  val xmlFileName = "$moduleSetName.xml"
  val descriptorPaths = getModuleSetDescriptorPaths(xmlFileName, ultimateRoot)
  val xmlPath = descriptorPaths.findExistingPath(moduleSetName, xmlFileName)
  return resolveModuleSetRecursive(xmlPath, ultimateRoot, embeddedOnly, HashSet())
}

private fun resolveModuleSetRecursive(xmlPath: Path, ultimateRoot: Path, embeddedOnly: Boolean, visited: MutableSet<Path>): List<String> {
  // Prevent infinite loops
  if (xmlPath in visited) {
    throw IllegalStateException("Circular x-include reference: $xmlPath")
  }

  visited.add(xmlPath)

  val doc = JDOMUtil.load(xmlPath)
  val result = mutableListOf<String>()

  // Extract modules from <content> tag
  val contentElement = doc.getChild("content")
  if (contentElement != null) {
    contentElement.getChildren("module")
      .filter { !embeddedOnly || it.getAttributeValue("loading") == "embedded" }
      .mapNotNullTo(result) { it.getAttribute("name")?.value }
  }

  // Recursively resolve x-include references to other module sets
  for (includeElement in doc.getChildren("include", JDOMUtil.XINCLUDE_NAMESPACE)) {
    val href = includeElement.getAttribute("href")?.value
    if (href != null && href.startsWith("/META-INF/intellij.moduleSets.")) {
      // extract module set name from href like "/META-INF/intellij.moduleSets.essential.xml"
      val includedFileName = href.substringAfterLast('/')
      val includedModuleSetName = includedFileName.removeSuffix(".xml")
      val includedDescriptorPaths = getModuleSetDescriptorPaths(includedFileName, ultimateRoot)
      val includedXmlPath = includedDescriptorPaths.findExistingPath(includedModuleSetName, includedFileName)
      result.addAll(resolveModuleSetRecursive(includedXmlPath, ultimateRoot, embeddedOnly, visited))
    }
  }

  return result
}

private fun getModuleSetDescriptorPaths(fileName: String, projectRoot: Path): ModuleSetDescriptorPaths {
  return ModuleSetDescriptorPaths(
    communityPath = projectRoot.resolve("community/platform/platform-resources/generated/META-INF/$fileName"),
    licenseCommonPath = projectRoot.resolve("licenseCommon/generated/META-INF/$fileName"),
  )
}

private fun ModuleSetDescriptorPaths.findExistingPath(moduleSetName: String, descriptorFileName: String): Path {
  if (communityPath.exists()) {
    return communityPath
  }
  if (licenseCommonPath.exists()) {
    return licenseCommonPath
  }

  throw MissingModuleSetDescriptorException(
    moduleSetName = moduleSetName,
    descriptorFileName = descriptorFileName,
    communityPath = communityPath,
    licenseCommonPath = licenseCommonPath,
  )
}

private fun Path.toDisplayPath(projectRoot: Path): String {
  return runCatching { relativeTo(projectRoot).invariantSeparatorsPathString }
    .getOrElse { invariantSeparatorsPathString }
}
