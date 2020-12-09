// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config

import com.intellij.openapi.components.PathMacroMap
import com.intellij.openapi.roots.JavadocOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.workspaceModel.ide.impl.jps.serialization.getLegacyLibraryName
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.eclipse.IdeaXml.*
import org.jetbrains.idea.eclipse.conversion.EPathUtil
import org.jetbrains.idea.eclipse.conversion.IdeaSpecificSettings
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension

/**
 * Saves additional module configuration from [ModuleEntity] to *.eml file
 */
internal class EmlFileSaver(private val module: ModuleEntity,
                            private val entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                            private val pathShortener: ModulePathShortener,
                            private val moduleReplacePathMacroMap: PathMacroMap,
                            private val projectReplacePathMacroMap: PathMacroMap) {
  fun saveEml(): Element? {
    val root = Element(COMPONENT_TAG)
    saveCustomJavaSettings(root)
    saveContentRoots(root)

    @Suppress("UNCHECKED_CAST")
    val moduleLibraries = (entities[LibraryEntity::class.java] as List<LibraryEntity>? ?: emptyList()).associateBy { it.name }
    val libLevels = LinkedHashMap<String, String>()
    module.dependencies.forEach { dep ->
      when (dep) {
        is ModuleDependencyItem.Exportable.ModuleDependency -> {
          if (dep.scope != ModuleDependencyItem.DependencyScope.COMPILE) {
            root.addContent(Element("module").setAttribute("name", dep.module.name).setAttribute("scope", dep.scope.name))
          }
        }
        is ModuleDependencyItem.InheritedSdkDependency -> {
          root.setAttribute(IdeaSpecificSettings.INHERIT_JDK, true.toString())
        }
        is ModuleDependencyItem.SdkDependency -> {
          root.setAttribute("jdk", dep.sdkName)
          root.setAttribute("jdk_type", dep.sdkType)
        }
        is ModuleDependencyItem.Exportable.LibraryDependency -> {
          val libTag = Element("lib")
          val library = moduleLibraries[dep.library.name]
          val libName = getLegacyLibraryName(dep.library) ?: generateLibName(library)
          libTag.setAttribute("name", libName)
          libTag.setAttribute("scope", dep.scope.name)
          when (val tableId = dep.library.tableId) {
            is LibraryTableId.ModuleLibraryTableId -> {
              if (library != null) {
                val srcRoots = library.roots.filter { it.type.name == OrderRootType.SOURCES.name() }
                val eclipseUrl = srcRoots.firstOrNull()?.url?.url?.substringBefore(JarFileSystem.JAR_SEPARATOR)
                srcRoots.forEach {
                  val url = it.url.url
                  val srcTag = Element(IdeaSpecificSettings.SRCROOT_ATTR).setAttribute("url", url)
                  if (!EPathUtil.areUrlsPointTheSame(url, eclipseUrl)) {
                    srcTag.setAttribute(IdeaSpecificSettings.SRCROOT_BIND_ATTR, false.toString())
                  }
                  libTag.addContent(srcTag)
                }
                library.roots.filter { it.type.name == "JAVADOC" }.drop(1).forEach {
                  libTag.addContent(Element(IdeaSpecificSettings.JAVADOCROOT_ATTR).setAttribute("url", it.url.url))
                }
                saveModuleRelatedRoots(libTag, library, OrderRootType.SOURCES, IdeaSpecificSettings.RELATIVE_MODULE_SRC)
                saveModuleRelatedRoots(libTag, library, OrderRootType.CLASSES, IdeaSpecificSettings.RELATIVE_MODULE_CLS)
                saveModuleRelatedRoots(libTag, library, JavadocOrderRootType.getInstance(), IdeaSpecificSettings.RELATIVE_MODULE_JAVADOC)
              }
            }
            is LibraryTableId.ProjectLibraryTableId -> libLevels[dep.library.name] = LibraryTablesRegistrar.PROJECT_LEVEL
            is LibraryTableId.GlobalLibraryTableId -> {
              if (tableId.level != LibraryTablesRegistrar.APPLICATION_LEVEL) {
                libLevels[dep.library.name] = tableId.level
              }
            }
          }
          if (libTag.children.isNotEmpty() || dep.scope != ModuleDependencyItem.DependencyScope.COMPILE) {
            root.addContent(libTag)
          }
        }
      }
    }
    if (libLevels.isNotEmpty()) {
      val levelsTag = Element("levels")
      for ((name, level) in libLevels) {
        levelsTag.addContent(Element("level").setAttribute("name", name).setAttribute("value", level))
      }
      root.addContent(levelsTag)
    }

    moduleReplacePathMacroMap.substitute(root, SystemInfo.isFileSystemCaseSensitive)
    return if (JDOMUtil.isEmpty(root)) null else root
  }

  private fun saveModuleRelatedRoots(libTag: Element, library: LibraryEntity, type: OrderRootType, tagName: @NonNls String) {
    library.roots.filter { it.type.name == type.name() }.forEach {
      val file = it.url.virtualFile
      val localFile = if (file?.fileSystem is JarFileSystem) JarFileSystem.getInstance().getVirtualFileForJar(file) else file
      if (localFile != null && pathShortener.isUnderContentRoots(localFile)) {
        libTag.addContent(Element(tagName).setAttribute(IdeaSpecificSettings.PROJECT_RELATED, projectReplacePathMacroMap.substitute(it.url.url, SystemInfo.isFileSystemCaseSensitive)))
      }
    }
  }

  private fun generateLibName(library: LibraryEntity?): String {
    val firstRoot = library?.roots?.firstOrNull { it.type.name == OrderRootType.CLASSES.name() }?.url
    val file = firstRoot?.virtualFile
    val fileForJar = JarFileSystem.getInstance().getVirtualFileForJar(file)
    if (fileForJar != null) return fileForJar.name
    return file?.name ?: "Empty Library"
  }

  private fun saveContentRoots(root: Element) {
    module.contentRoots.forEach { contentRoot ->
      val contentRootTag = Element(CONTENT_ENTRY_TAG).setAttribute(URL_ATTR, contentRoot.url.url)
      contentRoot.sourceRoots.forEach { sourceRoot ->
        if (sourceRoot.tests) {
          contentRootTag.addContent(Element(TEST_FOLDER_TAG).setAttribute(URL_ATTR, sourceRoot.url.url))
        }
        val packagePrefix = sourceRoot.asJavaSourceRoot()?.packagePrefix
        if (!packagePrefix.isNullOrEmpty()) {
          contentRootTag.addContent(Element(PACKAGE_PREFIX_TAG).setAttribute(URL_ATTR, sourceRoot.url.url)
                                      .setAttribute(PACKAGE_PREFIX_VALUE_ATTR, packagePrefix))
        }
      }
      val rootFile = contentRoot.url.virtualFile
      contentRoot.excludedUrls.forEach { excluded ->
        val excludedFile = excluded.virtualFile
        if (rootFile == null || excludedFile == null || VfsUtilCore.isAncestor(rootFile, excludedFile, false)) {
          contentRootTag.addContent(Element(EXCLUDE_FOLDER_TAG).setAttribute(URL_ATTR, excluded.url))
        }
      }
      if (!JDOMUtil.isEmpty(contentRootTag)) {
        root.addContent(contentRootTag)
      }
    }
  }

  private fun saveCustomJavaSettings(root: Element) {
    module.javaSettings?.let { javaSettings ->
      javaSettings.compilerOutputForTests?.let { testOutput ->
        root.addContent(Element(OUTPUT_TEST_TAG).setAttribute(URL_ATTR, testOutput.url))
      }
      if (javaSettings.inheritedCompilerOutput) {
        root.setAttribute(JpsJavaModelSerializerExtension.INHERIT_COMPILER_OUTPUT_ATTRIBUTE, true.toString())
      }
      if (javaSettings.excludeOutput) {
        root.addContent(Element(EXCLUDE_OUTPUT_TAG))
      }
    }

    module.customImlData?.rootManagerTagCustomData?.let { languageLevelTagString ->
      val tag = JDOMUtil.load(languageLevelTagString)
      tag.getAttributeValue("LANGUAGE_LEVEL")?.let {
        root.setAttribute("LANGUAGE_LEVEL", it)
      }
    }
  }

}