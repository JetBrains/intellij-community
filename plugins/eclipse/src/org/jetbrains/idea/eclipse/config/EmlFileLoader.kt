// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.asJavaSourceRoot
import com.intellij.java.workspace.entities.javaSettings
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.toPath
import org.jdom.Element
import org.jetbrains.idea.eclipse.IdeaXml
import org.jetbrains.idea.eclipse.conversion.EPathUtil
import org.jetbrains.idea.eclipse.conversion.IdeaSpecificSettings
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import org.jetbrains.jps.util.JpsPathUtil

/**
 * Loads additional module configuration from *.eml file to [ModuleEntity]
 */
internal class EmlFileLoader(
  private val module: ModuleEntity.Builder,
  private val expandMacroToPathMap: ExpandMacroToPathMap,
  private val virtualFileManager: VirtualFileUrlManager,
  private val moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity>,
) {
  fun loadEml(emlTag: Element, contentRoot: ContentRootEntity) {
    loadCustomJavaSettings(emlTag)
    loadContentEntries(emlTag, contentRoot)
    loadJdkSettings(emlTag)

    loadDependencies(emlTag)
  }

  private fun loadDependencies(emlTag: Element) {
    val moduleLibraries = moduleLibrariesCollector.values.associateBy { it.name }
    val libraryScopes = HashMap<String, ModuleDependencyItem.DependencyScope>()
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
          is ModuleDependencyItem.Exportable.LibraryDependency ->
            libraryScopes[dep.library.name]?.let { dep.copy(scope = it) } ?: dep
          is ModuleDependencyItem.Exportable.ModuleDependency ->
            moduleScopes[dep.module.name]?.let { dep.copy(scope = it) } ?: dep
          else -> dep
        }
      }
      dependencies = result
    }
  }

  private fun Element.getScope(): ModuleDependencyItem.DependencyScope {
    return getAttributeValue("scope")?.let {
      try {
        ModuleDependencyItem.DependencyScope.valueOf(it)
      }
      catch (e: IllegalArgumentException) {
        null
      }
    } ?: ModuleDependencyItem.DependencyScope.COMPILE
  }

  private fun loadModuleLibrary(libTag: Element, library: LibraryEntity) {
    val eclipseSrcRoot = library.roots.firstOrNull { it.type.name == OrderRootType.SOURCES.name() }
    val rootsToRemove = HashSet<LibraryRoot>()
    val rootsToAdd = ArrayList<LibraryRoot>()
    libTag.getChildren(IdeaSpecificSettings.SRCROOT_ATTR).forEach { rootTag ->
      val url = rootTag.getAttributeValue("url")
      val bindAttribute = rootTag.getAttributeValue(IdeaSpecificSettings.SRCROOT_BIND_ATTR)
      if (bindAttribute != null && !bindAttribute.toBoolean()) {
        rootsToAdd.add(LibraryRoot(virtualFileManager.getOrCreateFromUri(url!!), LibraryRootTypeId.SOURCES))
      }
      else if (eclipseSrcRoot != null && url != eclipseSrcRoot.url.url && EPathUtil.areUrlsPointTheSame(url, eclipseSrcRoot.url.url)) {
        rootsToAdd.add(LibraryRoot(virtualFileManager.getOrCreateFromUri(url!!), LibraryRootTypeId.SOURCES))
        rootsToRemove.add(eclipseSrcRoot)
      }
    }
    libTag.getChildren(IdeaSpecificSettings.JAVADOCROOT_ATTR).mapTo(rootsToAdd) {
      LibraryRoot(virtualFileManager.getOrCreateFromUri(it.getAttributeValue("url")!!), EclipseModuleRootsSerializer.JAVADOC_TYPE)
    }

    fun updateRoots(tagName: String, rootType: String) {
      libTag.getChildren(tagName).forEach { rootTag ->
        val root = expandMacroToPathMap.substitute(rootTag.getAttributeValue(IdeaSpecificSettings.PROJECT_RELATED)!!, SystemInfo.isFileSystemCaseSensitive)
        library.roots.forEach { libRoot ->
          if (libRoot !in rootsToRemove && libRoot.type.name == rootType && EPathUtil.areUrlsPointTheSame(root, libRoot.url.url)) {
            rootsToRemove.add(libRoot)
            rootsToAdd.add(LibraryRoot(virtualFileManager.getOrCreateFromUri(root), LibraryRootTypeId(rootType)))
          }
        }
      }
    }

    updateRoots(IdeaSpecificSettings.RELATIVE_MODULE_SRC, OrderRootType.SOURCES.name())
    updateRoots(IdeaSpecificSettings.RELATIVE_MODULE_CLS, OrderRootType.CLASSES.name())
    updateRoots(IdeaSpecificSettings.RELATIVE_MODULE_JAVADOC, "JAVADOC")
    if (rootsToAdd.isNotEmpty() || rootsToRemove.isNotEmpty()) {
      (library as LibraryEntity.Builder).apply {
        roots.removeAll(rootsToRemove)
        roots.addAll(rootsToAdd)
      }
    }
  }

  private fun loadJdkSettings(emlTag: Element) {
    val sdkItem = if (emlTag.getAttributeValue(IdeaSpecificSettings.INHERIT_JDK).toBoolean()) {
      ModuleDependencyItem.InheritedSdkDependency
    }
    else {
      emlTag.getAttributeValue("jdk")
        ?.let { ModuleDependencyItem.SdkDependency(it, "JavaSDK") }
    }

    if (sdkItem != null) {
      module.apply {
        val newDependencies = dependencies.map {
          when (it) {
            is ModuleDependencyItem.SdkDependency -> sdkItem
            ModuleDependencyItem.InheritedSdkDependency -> sdkItem
            else -> it
          }
        } as MutableList<ModuleDependencyItem>
        dependencies = if (newDependencies.size < dependencies.size) {
          val result = mutableListOf<ModuleDependencyItem>(ModuleDependencyItem.InheritedSdkDependency)
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
    (javaSettings as JavaModuleSettingsEntity.Builder).apply {
      val testOutputElement = emlTag.getChild(IdeaXml.OUTPUT_TEST_TAG)
      if (testOutputElement != null) {
        compilerOutputForTests = testOutputElement.getAttributeValue(IdeaXml.URL_ATTR)?.let { virtualFileManager.getOrCreateFromUri(it) }
      }

      val inheritedOutput = emlTag.getAttributeValue(JpsJavaModelSerializerExtension.INHERIT_COMPILER_OUTPUT_ATTRIBUTE)
      if (inheritedOutput.toBoolean()) {
        inheritedCompilerOutput = true
      }

      excludeOutput = emlTag.getChild(IdeaXml.EXCLUDE_OUTPUT_TAG) != null

      languageLevelId = emlTag.getAttributeValue("LANGUAGE_LEVEL")
    }
  }

  private fun loadContentEntries(emlTag: Element, contentRoot: ContentRootEntity) {
    val entryElements = emlTag.getChildren(IdeaXml.CONTENT_ENTRY_TAG)
    if (entryElements.isNotEmpty()) {
      entryElements.forEach { entryTag ->
        val url = virtualFileManager.getOrCreateFromUri(entryTag.getAttributeValue(IdeaXml.URL_ATTR)!!)
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

  private fun loadContentEntry(contentEntryTag: Element, entity: ContentRootEntity) {
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
      val rootType = if (isForTests) JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID else JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID
      if (rootType != sourceRoot.rootType) {
        (sourceRoot as SourceRootEntity.Builder).rootType = rootType
      }

      val packagePrefix = packagePrefixes[url]
      if (packagePrefix != null) {
        val javaRootProperties = sourceRoot.asJavaSourceRoot()
        if (javaRootProperties != null) {
          (javaRootProperties as JavaSourceRootPropertiesEntity.Builder).packagePrefix = packagePrefix
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
      .map { virtualFileManager.getOrCreateFromUri(it) }
    if (excludedUrls.isNotEmpty()) {
      (entity as ContentRootEntity.Builder).excludedUrls += excludedUrls.map { ExcludeUrlEntity(it, entity.entitySource) }
    }
  }
}