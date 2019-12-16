package com.intellij.workspace.jps

import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.PathUtil
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.toVirtualFileUrl
import com.intellij.workspace.ide.IdeUiEntitySource
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.JpsProjectStoragePlace
import junit.framework.AssertionFailedError
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Assert.*
import java.io.File

internal val sampleDirBasedProjectFile = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
internal val sampleFileBasedProjectFile = File(PathManagerEx.getCommunityHomePath(),
                                               "jps/model-serialization/testData/sampleProject-ipr/sampleProject.ipr")

internal data class LoadedProjectData(
  val storage: TypedEntityStorage,
  val serializationData: JpsEntitiesSerializationData,
  val projectDir: File,
  val originalProjectDir: File
) {
  val projectDirUrl: String
    get() = JpsPathUtil.pathToUrl(FileUtil.toSystemIndependentName(projectDir.absolutePath))
}

internal fun copyAndLoadProject(originalProjectFile: File): LoadedProjectData {
  val projectDir = FileUtil.createTempDirectory("jpsProjectTest", null)
  val originalProjectDir = if (originalProjectFile.isFile) originalProjectFile.parentFile else originalProjectFile
  FileUtil.copyDir(originalProjectDir, projectDir)
  val originalBuilder = TypedEntityStorageBuilder.create()
  val projectFile = if (originalProjectFile.isFile) File(projectDir, originalProjectFile.name) else projectDir
  val data = JpsProjectEntitiesLoader.loadProject(projectFile.asStoragePlace(), originalBuilder)
  val loadedProjectData = LoadedProjectData(originalBuilder.toStorage(), data, projectDir, originalProjectDir)
  data.checkConsistency(loadedProjectData.projectDirUrl, loadedProjectData.storage)
  return loadedProjectData
}

internal fun JpsEntitiesSerializationData.saveAllEntities(storage: TypedEntityStorage, projectDir: File) {
  val writer = JpsFileContentWriterImpl()
  saveAllEntities(storage, writer)
  writer.writeFiles(projectDir)
}

internal fun JpsFileContentWriterImpl.writeFiles(baseProjectDir: File) {
  filesToRemove.forEach {
    FileUtil.delete(JpsPathUtil.urlToFile(it))
  }
  urlToComponents.forEach { (url, newComponents) ->
    val components = HashMap(newComponents)
    val file = JpsPathUtil.urlToFile(url)

    val replaceMacroMap = if (FileUtil.extensionEquals(file.absolutePath, "iml"))
      CachingJpsFileContentReader.LegacyBridgeModulePathMacroManager(PathMacros.getInstance(), JpsPathUtil.urlToOsPath(url)).replacePathMap
    else
      CachingJpsFileContentReader.LegacyBridgeProjectPathMacroManager(baseProjectDir.systemIndependentPath).replacePathMap


    val newRootElement = when {
      file.extension == "iml" -> Element("module").setAttribute("type", "JAVA_MODULE")
      FileUtil.filesEqual(File(baseProjectDir, ".idea"), file.parentFile.parentFile) -> null
      else -> Element("project")
    }

    val rootElement: Element
    if (newRootElement != null) {
      newRootElement.setAttribute("version", "4")
      if (file.exists()) {
        val oldElement = JDOMUtil.load(file)
        oldElement.getChildren("component")
          .filterNot { it.getAttributeValue("name") in components }
          .map { it.clone() }
          .associateByTo(components) { it.getAttributeValue("name") }
      }
      components.entries.sortedBy { it.key }.forEach { (_, element) ->
        newRootElement.addContent(element)
      }
      rootElement = newRootElement
    }
    else {
      rootElement = components.values.single()
    }
    replaceMacroMap.substitute(rootElement, true, true)
    FileUtil.createParentDirs(file)
    JDOMUtil.write(rootElement, file)
  }
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

internal fun createSerializationData(projectDir: File): JpsEntitiesSerializationData {
  val reader = CachingJpsFileContentReader(VfsUtilCore.pathToUrl(projectDir.systemIndependentPath))
  return JpsProjectEntitiesLoader.createProjectSerializers(projectDir.asStoragePlace(), reader, true, true)
}

fun JpsEntitiesSerializationData.checkConsistency(projectBaseDirUrl: String, storage: TypedEntityStorage) {
  fun getNonNullActualFileUrl(source: JpsFileEntitySource) =
    getActualFileUrl(source) ?: throw AssertionFailedError("file name is not registered for $source")

  directorySerializerFactoriesByUrl.forEach { (url, directorySerializer) ->
    assertEquals(url, directorySerializer.directoryUrl)
    val fileSerializers = serializerToDirectoryFactory.getKeysByValue(directorySerializer)!!
    val directoryFileUrls = JpsPathUtil.urlToFile(url).listFiles { file: File -> file.isFile }?.map { JpsPathUtil.pathToUrl(it.systemIndependentPath) } ?: emptyList()
    assertEquals(directoryFileUrls.sorted(), fileSerializers.map { getNonNullActualFileUrl(it.entitySource) }.sorted())
  }

  fileSerializerFactoriesByUrl.forEach { (url, fileSerializer) ->
    assertEquals(url, fileSerializer.fileUrl)
    val fileSerializers = serializerToFileFactory.getKeysByValue(fileSerializer)!!
    val urlsFromFactory = fileSerializer.loadFileList(CachingJpsFileContentReader(projectBaseDirUrl))
    assertEquals(urlsFromFactory.map { it.url }.sorted(), fileSerializers.map { getNonNullActualFileUrl(it.entitySource) }.sorted())
  }

  fileSerializersByUrl.entrySet().forEach { (url, serializers) ->
    serializers.forEach {
      assertEquals(url, getNonNullActualFileUrl(it.entitySource))
    }
  }

  serializerToFileFactory.keys.forEach {
    assertTrue(it in fileSerializersByUrl[getNonNullActualFileUrl(it.entitySource)])
  }

  serializerToDirectoryFactory.keys.forEach {
    assertTrue(it in fileSerializersByUrl[getNonNullActualFileUrl(it.entitySource)])
  }

  val allSources = storage.entitiesBySource { true }
  assertNull(allSources[IdeUiEntitySource])
  val urlsFromSources = allSources.keys.filterIsInstance<JpsFileEntitySource>().mapTo(HashSet()) { getNonNullActualFileUrl(it) }
  urlsFromSources += fileSerializerFactoriesByUrl.keys
  assertEquals(urlsFromSources.sorted(), (fileSerializersByUrl.keySet() + fileSerializerFactoriesByUrl.keys).sorted())

  val fileIdFromEntities = allSources.keys.filterIsInstance(JpsFileEntitySource.FileInDirectory::class.java).mapTo(HashSet()) { it.fileNameId }
  val unregisteredIds = fileIdFromEntities - fileIdToFileName.keys().toSet()
  assertTrue("Some fileNameId aren't registered: ${unregisteredIds}", unregisteredIds.isEmpty())
  val staleIds = fileIdToFileName.keys().toSet() - fileIdFromEntities
  assertTrue("There are stale mapping for some fileNameId: ${staleIds.joinToString { "$it -> ${fileIdToFileName.get(it)}" }}", staleIds.isEmpty())
}

internal fun File.asStoragePlace(): JpsProjectStoragePlace =
  if (FileUtil.extensionEquals(name, "ipr")) JpsProjectStoragePlace.FileBased(toVirtualFileUrl())
  else JpsProjectStoragePlace.DirectoryBased(toVirtualFileUrl())

internal class JpsFileContentWriterImpl : JpsFileContentWriter {
  val urlToComponents = LinkedHashMap<String, LinkedHashMap<String, Element>>()
  val filesToRemove = LinkedHashSet<String>()

  override fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?) {
    if (componentTag != null) {
      urlToComponents.computeIfAbsent(fileUrl) { LinkedHashMap() }[componentName] = componentTag
    }
    else if (PathUtil.getFileName(PathUtil.getParentPath(PathUtil.getParentPath(fileUrl))) == ".idea" || FileUtil.extensionEquals(fileUrl,
                                                                                                                                  "iml")) {
      filesToRemove.add(fileUrl)
    }
  }
}