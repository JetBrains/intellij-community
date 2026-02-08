// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.config

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.asJavaSourceRoot
import com.intellij.java.workspace.entities.javaSettings
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ContentRootEntityBuilder
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntityBuilder
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.toPath
import org.jdom.Element
import org.jetbrains.idea.eclipse.IdeaXml
import org.jetbrains.idea.eclipse.conversion.EPathUtil
import org.jetbrains.idea.eclipse.conversion.IdeaSpecificSettings
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.util.JpsPathUtil

/**
 * Loads additional module configuration from *.eml file to [ModuleEntity]
 */
internal class EmlFileLoader(
  private val module: ModuleEntityBuilder,
  private val expandMacroToPathMap: ExpandMacroToPathMap,
  private val virtualFileManager: VirtualFileUrlManager,
  private val moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntityBuilder>,
) {
  fun loadEml(emlTag: Element, contentRoot: ContentRootEntityBuilder) {
    loadCustomJavaSettings(emlTag)
    loadContentEntries(emlTag, contentRoot)
    loadJdkSettings(emlTag)

    loadDependencies(emlTag)
  }

  private fun loadDependencies(emlTag: Element) {
    val moduleLibraries = moduleLibrariesCollector.values.associateBy { it.name }
    val libraryScopes = HashMap<String, DependencyScope>()
    emlTag.getChildren("lib").forEach { libTag ->
      val name = libTag.getAttributeValue("name")!!
      libraryScopes[name] = libTag.getScope()
      val moduleLibrary = moduleLibraries[name]
      if (moduleLibrary != null) {
        loadModuleLibrary(libTag, moduleLibrary)
      }
    }
    val moduleScopes = emlTag.getChildren("module").associateBy(
      { it.getAttributeValue("name") },
      { it.getScope() }
    )
    module.apply {
      val result = mutableListOf<ModuleDependencyItem>()
      dependencies.mapTo(result) { dep ->
        when (dep) {
          is LibraryDependency ->
            libraryScopes[dep.library.name]?.let { dep.copy(scope = it) } ?: dep
          is ModuleDependency ->
            moduleScopes[dep.module.name]?.let { dep.copy(scope = it) } ?: dep
          else -> dep
        }
      }
      dependencies = result
    }
  }

  private fun Element.getScope(): DependencyScope {
    return getAttributeValue("scope")?.let {
      try {
        DependencyScope.valueOf(it)
      }
      catch (e: IllegalArgumentException) {
        null
      }
    } ?: DependencyScope.COMPILE
  }

  private fun loadModuleLibrary(libTag: Element, library: LibraryEntityBuilder) {
    val eclipseSrcRoot = library.roots.firstOrNull { it.type.name == OrderRootType.SOURCES.name() }
    val rootsToRemove = HashSet<LibraryRoot>()
    val rootsToAdd = ArrayList<LibraryRoot>()
    libTag.getChildren(IdeaSpecificSettings.SRCROOT_ATTR).forEach { rootTag ->
      val url = rootTag.getAttributeValue("url")
      val bindAttribute = rootTag.getAttributeValue(IdeaSpecificSettings.SRCROOT_BIND_ATTR)
      if (bindAttribute != null && !bindAttribute.toBoolean()) {
        rootsToAdd.add(LibraryRoot(virtualFileManager.getOrCreateFromUrl(url!!), LibraryRootTypeId.SOURCES))
      }
      else if (eclipseSrcRoot != null && url != eclipseSrcRoot.url.url && EPathUtil.areUrlsPointTheSame(url, eclipseSrcRoot.url.url)) {
        rootsToAdd.add(LibraryRoot(virtualFileManager.getOrCreateFromUrl(url!!), LibraryRootTypeId.SOURCES))
        rootsToRemove.add(eclipseSrcRoot)
      }
    }
    libTag.getChildren(IdeaSpecificSettings.JAVADOCROOT_ATTR).mapTo(rootsToAdd) {
      LibraryRoot(virtualFileManager.getOrCreateFromUrl(it.getAttributeValue("url")!!), EclipseModuleRootsSerializer.JAVADOC_TYPE)
    }

    fun updateRoots(tagName: String, rootType: String) {
      libTag.getChildren(tagName).forEach { rootTag ->
        val root = expandMacroToPathMap.substitute(rootTag.getAttributeValue(IdeaSpecificSettings.PROJECT_RELATED)!!, SystemInfo.isFileSystemCaseSensitive)
        library.roots.forEach { libRoot ->
          if (libRoot !in rootsToRemove && libRoot.type.name == rootType && EPathUtil.areUrlsPointTheSame(root, libRoot.url.url)) {
            rootsToRemove.add(libRoot)
            rootsToAdd.add(LibraryRoot(virtualFileManager.getOrCreateFromUrl(root), LibraryRootTypeId(rootType)))
          }
        }
      }
    }

    updateRoots(IdeaSpecificSettings.RELATIVE_MODULE_SRC, OrderRootType.SOURCES.name())
    updateRoots(IdeaSpecificSettings.RELATIVE_MODULE_CLS, OrderRootType.CLASSES.name())
    updateRoots(IdeaSpecificSettings.RELATIVE_MODULE_JAVADOC, "JAVADOC")
    if (rootsToAdd.isNotEmpty() || rootsToRemove.isNotEmpty()) {
      library.apply {
        roots.removeAll(rootsToRemove)
        roots.addAll(rootsToAdd)
      }
    }
  }

  private fun loadJdkSettings(emlTag: Element) {
    val sdkItem = if (emlTag.getAttributeValue(IdeaSpecificSettings.INHERIT_JDK).toBoolean()) {
      InheritedSdkDependency
    }
    else {
      emlTag.getAttributeValue("jdk")?.let { SdkDependency(SdkId(it, "JavaSDK")) }
    }

    if (sdkItem != null) {
      module.apply {
        val newDependencies = dependencies.map {
          when (it) {
            is SdkDependency -> sdkItem
            InheritedSdkDependency -> sdkItem
            else -> it
          }
        } as MutableList<ModuleDependencyItem>
        dependencies = if (newDependencies.size < dependencies.size) {
          val result = mutableListOf<ModuleDependencyItem>(InheritedSdkDependency)
          result.addAll(newDependencies)
          result
        }
        else newDependencies
      }
    }
  }

  private fun loadCustomJavaSettings(emlTag: Element) {
    val javaSettings = module.javaSettings ?: JavaModuleSettingsEntity(true, true, module.entitySource) {
      this.module = module
    }
    javaSettings.apply {
      val testOutputElement = emlTag.getChild(IdeaXml.OUTPUT_TEST_TAG)
      if (testOutputElement != null) {
        compilerOutputForTests = testOutputElement.getAttributeValue(IdeaXml.URL_ATTR)?.let { virtualFileManager.getOrCreateFromUrl(it) }
      }

      val inheritedOutput = emlTag.getAttributeValue(JpsJavaModelSerializerExtension.INHERIT_COMPILER_OUTPUT_ATTRIBUTE)
      if (inheritedOutput.toBoolean()) {
        inheritedCompilerOutput = true
      }

      excludeOutput = emlTag.getChild(IdeaXml.EXCLUDE_OUTPUT_TAG) != null

      languageLevelId = emlTag.getAttributeValue("LANGUAGE_LEVEL")
    }
  }

  private fun loadContentEntries(emlTag: Element, contentRoot: ContentRootEntityBuilder) {
    val entryElements = emlTag.getChildren(IdeaXml.CONTENT_ENTRY_TAG)
    if (entryElements.isNotEmpty()) {
      entryElements.forEach { entryTag ->
        val url = virtualFileManager.getOrCreateFromUrl(entryTag.getAttributeValue(IdeaXml.URL_ATTR)!!)
        val contentRootEntity = contentRoot.module.contentRoots.firstOrNull { it.url == url }
                                ?: ContentRootEntity(url, emptyList(), module.entitySource) {
                                  this.module = module
                                }
        loadContentEntry(entryTag, contentRootEntity)
      }
    }
    else {
      loadContentEntry(emlTag, contentRoot)
    }
  }

  private fun loadContentEntry(contentEntryTag: Element, entity: ContentRootEntityBuilder) {
    val testSourceFolders = contentEntryTag.getChildren(IdeaXml.TEST_FOLDER_TAG).mapTo(HashSet()) {
      it.getAttributeValue(IdeaXml.URL_ATTR)
    }
    val packagePrefixes = contentEntryTag.getChildren(IdeaXml.PACKAGE_PREFIX_TAG).associateBy(
      { it.getAttributeValue(IdeaXml.URL_ATTR)!! },
      { it.getAttributeValue(IdeaXml.PACKAGE_PREFIX_VALUE_ATTR)!! }
    )
    for (sourceRoot in entity.sourceRoots) {
      val url = sourceRoot.url.url
      val isForTests = url in testSourceFolders
      val rootTypeId = if (isForTests) JAVA_TEST_ROOT_ENTITY_TYPE_ID else JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
      if (rootTypeId != sourceRoot.rootTypeId) {
        sourceRoot.rootTypeId = rootTypeId
      }

      val packagePrefix = packagePrefixes[url]
      if (packagePrefix != null) {
        val javaRootProperties = sourceRoot.asJavaSourceRoot()
        if (javaRootProperties != null) {
          javaRootProperties.packagePrefix = packagePrefix
        }
        else {
          JavaSourceRootPropertiesEntity(false, packagePrefix, sourceRoot.entitySource) {
            this.sourceRoot = sourceRoot
          }
        }
      }
    }

    val excludedUrls = contentEntryTag.getChildren(IdeaXml.EXCLUDE_FOLDER_TAG)
      .mapNotNull { it.getAttributeValue(IdeaXml.URL_ATTR) }
      .filter { FileUtil.isAncestor(entity.url.toPath().toFile(), JpsPathUtil.urlToFile(it), false) }
      .map { virtualFileManager.getOrCreateFromUrl(it) }
    if (excludedUrls.isNotEmpty()) {
      entity.excludedUrls += excludedUrls.map { ExcludeUrlEntity(it, entity.entitySource) }
    }
  }
}