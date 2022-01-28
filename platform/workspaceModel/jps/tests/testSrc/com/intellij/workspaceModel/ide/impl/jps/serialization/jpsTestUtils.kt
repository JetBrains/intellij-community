// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.components.PathMacroMap
import com.intellij.openapi.components.impl.ModulePathMacroManager
import com.intellij.openapi.components.impl.ProjectPathMacroManager
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.UsefulTestCase
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.url.toVirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import junit.framework.AssertionFailedError
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.nio.file.Path

internal val sampleDirBasedProjectFile = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
internal val sampleFileBasedProjectFile = File(PathManagerEx.getCommunityHomePath(),
                                               "jps/model-serialization/testData/sampleProject-ipr/sampleProject.ipr")

internal data class LoadedProjectData(
  val storage: WorkspaceEntityStorage,
  val serializers: JpsProjectSerializersImpl,
  val configLocation: JpsProjectConfigLocation,
  val originalProjectDir: File
) {
  val projectDirUrl: String
    get() = configLocation.baseDirectoryUrlString
  val projectDir: File
    get() = File(VfsUtilCore.urlToPath(configLocation.baseDirectoryUrlString))
}

internal fun copyAndLoadProject(originalProjectFile: File, virtualFileManager: VirtualFileUrlManager): LoadedProjectData {
  val (projectDir, originalProjectDir) = copyProjectFiles(originalProjectFile)
  val originalBuilder = WorkspaceEntityStorageBuilder.create()
  val projectFile = if (originalProjectFile.isFile) File(projectDir, originalProjectFile.name) else projectDir
  val configLocation = toConfigLocation(projectFile.toPath(), virtualFileManager)
  val serializers = loadProject(configLocation, originalBuilder, virtualFileManager) as JpsProjectSerializersImpl
  val loadedProjectData = LoadedProjectData(originalBuilder.toStorage(), serializers, configLocation, originalProjectDir)
  serializers.checkConsistency(loadedProjectData.projectDirUrl, loadedProjectData.storage, virtualFileManager)
  return loadedProjectData
}

internal fun copyProjectFiles(originalProjectFile: File): Pair<File, File> {
  val projectDir = FileUtil.createTempDirectory("jpsProjectTest", null)
  val originalProjectDir = if (originalProjectFile.isFile) originalProjectFile.parentFile else originalProjectFile
  FileUtil.copyDir(originalProjectDir, projectDir)
  return projectDir to originalProjectDir
}

internal fun loadProject(configLocation: JpsProjectConfigLocation, originalBuilder: WorkspaceEntityStorageBuilder, virtualFileManager: VirtualFileUrlManager,
                         fileInDirectorySourceNames: FileInDirectorySourceNames = FileInDirectorySourceNames.empty(),
                         externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null): JpsProjectSerializers {
  val cacheDirUrl = configLocation.baseDirectoryUrl.append("cache")
  return JpsProjectEntitiesLoader.loadProject(configLocation, originalBuilder, File(VfsUtil.urlToPath(cacheDirUrl.url)).toPath(),
                                              TestErrorReporter, virtualFileManager, fileInDirectorySourceNames,
                                              externalStorageConfigurationManager)
}

internal fun JpsProjectSerializersImpl.saveAllEntities(storage: WorkspaceEntityStorage, projectDir: File) {
  val writer = JpsFileContentWriterImpl(projectDir)
  saveAllEntities(storage, writer)
  writer.writeFiles()
}

internal fun assertDirectoryMatches(actualDir: File, expectedDir: File, filesToIgnore: Set<String>, componentsToIgnore: List<String>) {
  val actualFiles = actualDir.walk().filter { it.isFile }.associateBy { FileUtil.toSystemIndependentName(FileUtil.getRelativePath(actualDir, it)!!) }
  val expectedFiles = expectedDir.walk()
    .filter { it.isFile }
    .associateBy { FileUtil.toSystemIndependentName(FileUtil.getRelativePath(expectedDir, it)!!) }
    .filterKeys { it !in filesToIgnore }

  for (actualPath in actualFiles.keys) {
    val actualFile = actualFiles.getValue(actualPath)
    if (actualPath in expectedFiles) {
      val expectedFile = expectedFiles.getValue(actualPath)
      if (actualFile.extension in setOf("xml", "iml", "ipr")) {
        val expectedTag = JDOMUtil.load(expectedFile)
        componentsToIgnore.forEach {
          val toIgnore = JDomSerializationUtil.findComponent(expectedTag, it)
          if (toIgnore != null) {
            expectedTag.removeContent(toIgnore)
          }
        }
        val actualTag = JDOMUtil.load(actualFile)
        assertEquals("Different content in $actualPath", JDOMUtil.write(expectedTag), JDOMUtil.write(actualTag))
      }
      else {
        assertEquals("Different content in $actualPath", expectedFile.readText(), actualFile.readText())
      }
    }
  }
  UsefulTestCase.assertEmpty(actualFiles.keys - expectedFiles.keys)
  UsefulTestCase.assertEmpty(expectedFiles.keys - actualFiles.keys)
}

internal fun createProjectSerializers(projectDir: File, virtualFileManager: VirtualFileUrlManager): JpsProjectSerializersImpl {
  val reader = CachingJpsFileContentReader(VfsUtilCore.pathToUrl(projectDir.systemIndependentPath))
  val externalStoragePath = projectDir.toPath().resolve("cache")
  return JpsProjectEntitiesLoader.createProjectSerializers(toConfigLocation(projectDir.toPath(), virtualFileManager), reader,
                                                           externalStoragePath, true, virtualFileManager) as JpsProjectSerializersImpl
}

fun JpsProjectSerializersImpl.checkConsistency(projectBaseDirUrl: String, storage: WorkspaceEntityStorage, virtualFileManager: VirtualFileUrlManager) {
  fun getNonNullActualFileUrl(source: EntitySource): String {
    return getActualFileUrl(source) ?: throw AssertionFailedError("file name is not registered for $source")
  }

  directorySerializerFactoriesByUrl.forEach { (url, directorySerializer) ->
    assertEquals(url, directorySerializer.directoryUrl)
    val fileSerializers = serializerToDirectoryFactory.getKeysByValue(directorySerializer) ?: emptyList()
    val directoryFileUrls = JpsPathUtil.urlToFile(url).listFiles { file: File -> file.isFile }?.map { JpsPathUtil.pathToUrl(it.systemIndependentPath) } ?: emptyList()
    assertEquals(directoryFileUrls.sorted(), fileSerializers.map { getNonNullActualFileUrl(it.internalEntitySource) }.sorted())
  }

  moduleListSerializersByUrl.forEach { (url, fileSerializer) ->
    assertEquals(url, fileSerializer.fileUrl)
    val fileSerializers = moduleSerializers.getKeysByValue(fileSerializer) ?: emptyList()
    val urlsFromFactory = fileSerializer.loadFileList(CachingJpsFileContentReader(projectBaseDirUrl), virtualFileManager)
    assertEquals(urlsFromFactory.map { it.first.url }.sorted(), fileSerializers.map { getNonNullActualFileUrl(it.internalEntitySource) }.sorted())
  }

  fileSerializersByUrl.keys.associateWith { fileSerializersByUrl.getValues(it) }.forEach { (url, serializers) ->
    serializers.forEach {
      assertEquals(url, getNonNullActualFileUrl(it.internalEntitySource))
    }
  }

  moduleSerializers.keys.forEach {
    assertTrue(it in fileSerializersByUrl.getValues(getNonNullActualFileUrl(it.internalEntitySource)))
  }

  serializerToDirectoryFactory.keys.forEach {
    assertTrue(it in fileSerializersByUrl.getValues(getNonNullActualFileUrl(it.internalEntitySource)))
  }

  fun <E : WorkspaceEntity> isSerializerWithoutEntities(serializer: JpsFileEntitiesSerializer<E>) =
    serializer is JpsFileEntityTypeSerializer<E> && storage.entities(serializer.mainEntityClass).none { serializer.entityFilter(it) }

  val allSources = storage.entitiesBySource { true }
  val urlsFromSources = allSources.keys.filterIsInstance<JpsFileEntitySource>().mapTo(HashSet()) { getNonNullActualFileUrl(it) }
  assertEquals(urlsFromSources.sorted(), fileSerializersByUrl.keys.associateWith { fileSerializersByUrl.getValues(it) }
    .filterNot { entry -> entry.value.all { isSerializerWithoutEntities(it)} }.map { it.key }.sorted())

  val fileIdFromEntities = allSources.keys.filterIsInstance(JpsFileEntitySource.FileInDirectory::class.java).mapTo(HashSet()) { it.fileNameId }
  val unregisteredIds = fileIdFromEntities - fileIdToFileName.keys.toSet()
  assertTrue("Some fileNameId aren't registered: ${unregisteredIds}", unregisteredIds.isEmpty())
  val staleIds = fileIdToFileName.keys.toSet() - fileIdFromEntities
  assertTrue("There are stale mapping for some fileNameId: ${staleIds.joinToString { "$it -> ${fileIdToFileName.get(it)}" }}", staleIds.isEmpty())
}

internal fun File.asConfigLocation(virtualFileManager: VirtualFileUrlManager): JpsProjectConfigLocation = toConfigLocation(toPath(), virtualFileManager)

internal fun toConfigLocation(file: Path, virtualFileManager: VirtualFileUrlManager): JpsProjectConfigLocation {
  if (FileUtil.extensionEquals(file.fileName.toString(), "ipr")) {
    val iprFile = file.toVirtualFileUrl(virtualFileManager)
    return JpsProjectConfigLocation.FileBased(iprFile, virtualFileManager.getParentVirtualUrl(iprFile)!!)
  }
  else {
    val projectDir = file.toVirtualFileUrl(virtualFileManager)
    return JpsProjectConfigLocation.DirectoryBased(projectDir, projectDir.append(PathMacroUtil.DIRECTORY_STORE_NAME))
  }
}

internal class JpsFileContentWriterImpl(private val baseProjectDir: File) : JpsFileContentWriter {
  val urlToComponents = LinkedHashMap<String, LinkedHashMap<String, Element?>>()

  override fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?) {
    urlToComponents.computeIfAbsent(fileUrl) { LinkedHashMap() }[componentName] = componentTag
  }

  override fun getReplacePathMacroMap(fileUrl: String): PathMacroMap {
    return if (isModuleFile(JpsPathUtil.urlToFile(fileUrl)))
      ModulePathMacroManager.createInstance { JpsPathUtil.urlToOsPath(fileUrl) }.replacePathMap
    else
      ProjectPathMacroManager.createInstance({ baseProjectDir.systemIndependentPath }, null).replacePathMap
  }

  internal fun writeFiles() {
    urlToComponents.forEach { (url, newComponents) ->
      val components = HashMap(newComponents)
      val file = JpsPathUtil.urlToFile(url)
      val replaceMacroMap = getReplacePathMacroMap(url)
      val newRootElement = when {
        isModuleFile(file) -> Element("module")
        FileUtil.filesEqual(File(baseProjectDir, ".idea"), file.parentFile.parentFile) -> null
        else -> Element("project")
      }

      fun isEmptyComponentTag(componentTag: Element) = componentTag.contentSize == 0 && componentTag.attributes.all { it.name == "name" }

      val rootElement: Element?
      if (newRootElement != null) {
        if (file.exists()) {
          val oldElement = JDOMUtil.load(file)
          oldElement.getChildren("component")
            .filterNot { it.getAttributeValue("name") in components }
            .map { it.clone() }
            .associateByTo(components) { it.getAttributeValue("name") }
        }
        components.entries.sortedBy { it.key }.forEach { (name, element) ->
          if (element != null && !isEmptyComponentTag(element)) {
            if (name == DEPRECATED_MODULE_MANAGER_COMPONENT_NAME) {
              element.getChildren("option").forEach {
                newRootElement.setAttribute(it.getAttributeValue("key")!!, it.getAttributeValue("value")!!)
              }
            }
            else {
              newRootElement.addContent(element)
            }
          }
        }
        if (!JDOMUtil.isEmpty(newRootElement)) {
          newRootElement.setAttribute("version", "4")
          rootElement = newRootElement
        }
        else {
          rootElement = null
        }
      }
      else {
        val singleComponent = components.values.single()
        rootElement = if (singleComponent != null && !isEmptyComponentTag(singleComponent)) singleComponent else null
      }
      if (rootElement != null) {
        replaceMacroMap.substitute(rootElement, true, true)
        FileUtil.createParentDirs(file)
        JDOMUtil.write(rootElement, file)
      }
      else {
        FileUtil.delete(file)
      }
    }
  }

  private fun isModuleFile(file: File) = (FileUtil.extensionEquals(file.absolutePath, "iml")
                                          || file.parentFile.name == "modules" && file.parentFile.parentFile.name != ".idea")
}

internal object TestErrorReporter : ErrorReporter {
  override fun reportError(message: String, file: VirtualFileUrl) {
    throw AssertionFailedError("Failed to load ${file.url}: $message")
  }
}