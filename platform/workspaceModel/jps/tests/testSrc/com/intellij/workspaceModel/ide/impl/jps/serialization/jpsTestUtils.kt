// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
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
import com.intellij.util.LineSeparator
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.FileInDirectorySourceNames
import com.intellij.workspaceModel.storage.*
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
import java.util.function.Supplier

internal val sampleDirBasedProjectFile = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
internal val sampleFileBasedProjectFile = File(PathManagerEx.getCommunityHomePath(),
                                               "jps/model-serialization/testData/sampleProject-ipr/sampleProject.ipr")

internal data class LoadedProjectData(
  val storage: EntityStorageSnapshot,
  val serializers: JpsProjectSerializersImpl,
  val configLocation: JpsProjectConfigLocation,
  val originalProjectDir: File
) {
  val projectDirUrl: String
    get() = configLocation.baseDirectoryUrlString
  val projectDir: File
    get() = File(VfsUtilCore.urlToPath(configLocation.baseDirectoryUrlString))
}

internal fun copyAndLoadProject(originalProjectFile: File,
                                virtualFileManager: VirtualFileUrlManager,
                                checkConsistencyAfterLoading: Boolean = true,
                                externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null): LoadedProjectData {
  val (projectDir, originalProjectDir) = copyProjectFiles(originalProjectFile)
  val originalBuilder = MutableEntityStorage.create()
  val projectFile = if (originalProjectFile.isFile) File(projectDir, originalProjectFile.name) else projectDir
  val configLocation = toConfigLocation(projectFile.toPath(), virtualFileManager)
  val serializers = loadProject(configLocation, originalBuilder, virtualFileManager, externalStorageConfigurationManager = externalStorageConfigurationManager) as JpsProjectSerializersImpl
  val loadedProjectData = LoadedProjectData(originalBuilder.toSnapshot(), serializers, configLocation, originalProjectDir)
  if (checkConsistencyAfterLoading) {
    serializers.checkConsistency(loadedProjectData.configLocation, loadedProjectData.storage, virtualFileManager)
  }
  return loadedProjectData
}

internal fun copyProjectFiles(originalProjectFile: File): Pair<File, File> {
  val projectDir = FileUtil.createTempDirectory("jpsProjectTest", null)
  val originalProjectDir = if (originalProjectFile.isFile) originalProjectFile.parentFile else originalProjectFile
  FileUtil.copyDir(originalProjectDir, projectDir)
  return projectDir to originalProjectDir
}

internal fun loadProject(configLocation: JpsProjectConfigLocation, originalBuilder: MutableEntityStorage, virtualFileManager: VirtualFileUrlManager,
                         fileInDirectorySourceNames: FileInDirectorySourceNames = FileInDirectorySourceNames.empty(),
                         externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null): JpsProjectSerializers {
  val cacheDirUrl = configLocation.baseDirectoryUrl.append("cache")
  return runUnderModalProgressIfIsEdt {
    JpsProjectEntitiesLoader.loadProject(configLocation, originalBuilder, File(VfsUtil.urlToPath(cacheDirUrl.url)).toPath(),
                                         TestErrorReporter, virtualFileManager, fileInDirectorySourceNames,
                                         externalStorageConfigurationManager)
  }
}

fun JpsProjectSerializersImpl.saveAllEntities(storage: EntityStorage, configLocation: JpsProjectConfigLocation) {
  val writer = JpsFileContentWriterImpl(configLocation)
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

internal fun createProjectSerializers(projectDir: File,
                                      virtualFileManager: VirtualFileUrlManager): Pair<JpsProjectSerializersImpl, JpsProjectConfigLocation> {
  val configLocation = toConfigLocation(projectDir.toPath(), virtualFileManager)
  val reader = CachingJpsFileContentReader(configLocation)
  val externalStoragePath = projectDir.toPath().resolve("cache")
  val serializer = JpsProjectEntitiesLoader.createProjectSerializers(configLocation, reader, externalStoragePath,
                                                                     virtualFileManager) as JpsProjectSerializersImpl
  return serializer to configLocation
}

fun JpsProjectSerializersImpl.checkConsistency(configLocation: JpsProjectConfigLocation,
                                               storage: EntityStorage,
                                               virtualFileManager: VirtualFileUrlManager) {
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
    val urlsFromFactory = fileSerializer.loadFileList(CachingJpsFileContentReader(configLocation), virtualFileManager)
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

internal class JpsFileContentWriterImpl(private val configLocation: JpsProjectConfigLocation) : JpsFileContentWriter {
  private val urlToComponents = LinkedHashMap<String, LinkedHashMap<String, Element?>>()
  private val XML_PROLOG = """<?xml version="1.0" encoding="UTF-8"?>""".toByteArray()

  override fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?) {
    urlToComponents.computeIfAbsent(fileUrl) { LinkedHashMap() }[componentName] = componentTag
  }

  override fun getReplacePathMacroMap(fileUrl: String): PathMacroMap {
    return if (isModuleFile(JpsPathUtil.urlToFile(fileUrl)))
      ModulePathMacroManager.createInstance(configLocation::projectFilePath, Supplier { JpsPathUtil.urlToOsPath(fileUrl) }).replacePathMap
    else
      ProjectPathMacroManager.createInstance(configLocation::projectFilePath,
                                             { JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString) },
                                             null).replacePathMap
  }

  internal fun writeFiles() {
    urlToComponents.forEach { (url, newComponents) ->
      val components = HashMap(newComponents)
      val file = JpsPathUtil.urlToFile(url)
      val replaceMacroMap = getReplacePathMacroMap(url)
      val newRootElement = when {
        isModuleFile(file) -> Element("module")
        FileUtil.filesEqual(File(JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString), ".idea"), file.parentFile.parentFile) -> null
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
        file.outputStream().use {
          if (isModuleFile(file)) {
            it.write(XML_PROLOG)
            it.write(LineSeparator.LF.separatorBytes)
          }
          JDOMUtil.write(rootElement, it)
        }
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

internal fun checkSaveProjectAfterChange(originalProjectFile: File,
                                         changedFilesDirectoryName: String?,
                                         change: (MutableEntityStorage, JpsProjectConfigLocation) -> Unit,
                                         virtualFileManager: VirtualFileUrlManager,
                                         testDir: String,
                                         checkConsistencyAfterLoading: Boolean = true,
                                         externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null) {
  val projectData = copyAndLoadProject(originalProjectFile, virtualFileManager, checkConsistencyAfterLoading, externalStorageConfigurationManager)
  val builder = MutableEntityStorage.from(projectData.storage)
  change(builder, projectData.configLocation)
  val changesMap = builder.collectChanges(projectData.storage)
  val changedSources = changesMap.values.flatMapTo(HashSet()) { changes ->
    changes.flatMap { change ->
      when (change) {
        is EntityChange.Added -> listOf(change.entity)
        is EntityChange.Removed -> listOf(change.entity)
        is EntityChange.Replaced -> listOf(change.oldEntity, change.newEntity)
      }
    }.map { it.entitySource }
  }
  val writer = JpsFileContentWriterImpl(projectData.configLocation)
  projectData.serializers.saveEntities(builder.toSnapshot(), changedSources, writer)
  writer.writeFiles()
  if (checkConsistencyAfterLoading) {
    projectData.serializers.checkConsistency(projectData.configLocation, builder.toSnapshot(), virtualFileManager)
  }

  val expectedDir = FileUtil.createTempDirectory("jpsProjectTest", "expected")
  FileUtil.copyDir(projectData.originalProjectDir, expectedDir)
  if (changedFilesDirectoryName != null) {
    val changedDir = PathManagerEx.findFileUnderCommunityHome(
      "platform/workspaceModel/jps/tests/testData/$testDir/$changedFilesDirectoryName")
    FileUtil.copyDir(changedDir, expectedDir)
  }
  expectedDir.walk().filter { it.isFile && it.readText().trim() == "<delete/>" }.forEach {
    FileUtil.delete(it)
  }

  assertDirectoryMatches(projectData.projectDir, expectedDir, emptySet(), emptyList())
}
