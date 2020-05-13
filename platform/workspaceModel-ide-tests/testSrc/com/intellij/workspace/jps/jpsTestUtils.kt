package com.intellij.workspace.jps

import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.UsefulTestCase
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.JpsProjectConfigLocation
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
  val projectDir = FileUtil.createTempDirectory("jpsProjectTest", null)
  val originalProjectDir = if (originalProjectFile.isFile) originalProjectFile.parentFile else originalProjectFile
  FileUtil.copyDir(originalProjectDir, projectDir)
  val originalBuilder = TypedEntityStorageBuilder.create()
  val projectFile = if (originalProjectFile.isFile) File(projectDir, originalProjectFile.name) else projectDir
  val configLocation = projectFile.asConfigLocation(virtualFileManager)
  val serializers = loadProject(configLocation, originalBuilder, virtualFileManager) as JpsProjectSerializersImpl
  val loadedProjectData = LoadedProjectData(originalBuilder.toStorage(), serializers, configLocation, originalProjectDir)
  serializers.checkConsistency(loadedProjectData.projectDirUrl, loadedProjectData.storage, virtualFileManager)
  return loadedProjectData
}

internal fun   loadProject(configLocation: JpsProjectConfigLocation, originalBuilder: TypedEntityStorageBuilder, virtualFileManager: VirtualFileUrlManager): JpsProjectSerializers {
  val cacheDirUrl = configLocation.baseDirectoryUrl.append("cache")
  return JpsProjectEntitiesLoader.loadProject(configLocation, originalBuilder, File(VfsUtil.urlToPath(cacheDirUrl.url)).toPath(), virtualFileManager)
}

internal fun JpsProjectSerializersImpl.saveAllEntities(storage: TypedEntityStorage, projectDir: File) {
  val writer = JpsFileContentWriterImpl()
  saveAllEntities(storage, writer)
  writer.writeFiles(projectDir)
}

internal fun JpsFileContentWriterImpl.writeFiles(baseProjectDir: File) {
  urlToComponents.forEach { (url, newComponents) ->
    val components = HashMap(newComponents)
    val file = JpsPathUtil.urlToFile(url)

    val isModuleFile = FileUtil.extensionEquals(file.absolutePath, "iml")
                       || file.parentFile.name == "modules" && file.parentFile.parentFile.name != ".idea"
    val replaceMacroMap = if (isModuleFile)
      CachingJpsFileContentReader.LegacyBridgeModulePathMacroManager(PathMacros.getInstance(), JpsPathUtil.urlToOsPath(url)).replacePathMap
    else
      CachingJpsFileContentReader.LegacyBridgeProjectPathMacroManager(baseProjectDir.systemIndependentPath).replacePathMap


    val newRootElement = when {
      isModuleFile -> Element("module")
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
          if (name == "DeprecatedModuleOptionManager") {
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
  return JpsProjectEntitiesLoader.createProjectSerializers(projectDir.asConfigLocation(virtualFileManager), reader, externalStoragePath, true, virtualFileManager) as JpsProjectSerializersImpl
}

fun JpsProjectSerializersImpl.checkConsistency(projectBaseDirUrl: String, storage: TypedEntityStorage, virtualFileManager: VirtualFileUrlManager) {
  fun getNonNullActualFileUrl(source: EntitySource): String {
    return getActualFileUrl(source) ?: throw AssertionFailedError("file name is not registered for $source")
  }

  directorySerializerFactoriesByUrl.forEach { (url, directorySerializer) ->
    assertEquals(url, directorySerializer.directoryUrl)
    val fileSerializers = serializerToDirectoryFactory.getKeysByValue(directorySerializer)!!
    val directoryFileUrls = JpsPathUtil.urlToFile(url).listFiles { file: File -> file.isFile }?.map { JpsPathUtil.pathToUrl(it.systemIndependentPath) } ?: emptyList()
    assertEquals(directoryFileUrls.sorted(), fileSerializers.map { getNonNullActualFileUrl(it.internalEntitySource) }.sorted())
  }

  fileSerializerFactoriesByUrl.forEach { (url, fileSerializer) ->
    assertEquals(url, fileSerializer.fileUrl)
    val fileSerializers = serializerToFileFactory.getKeysByValue(fileSerializer) ?: emptyList()
    val urlsFromFactory = fileSerializer.loadFileList(CachingJpsFileContentReader(projectBaseDirUrl), virtualFileManager)
    assertEquals(urlsFromFactory.map { it.url }.sorted(), fileSerializers.map { getNonNullActualFileUrl(it.internalEntitySource) }.sorted())
  }

  fileSerializersByUrl.entrySet().forEach { (url, serializers) ->
    serializers.forEach {
      assertEquals(url, getNonNullActualFileUrl(it.internalEntitySource))
    }
  }

  serializerToFileFactory.keys.forEach {
    assertTrue(it in fileSerializersByUrl[getNonNullActualFileUrl(it.internalEntitySource)])
  }

  serializerToDirectoryFactory.keys.forEach {
    assertTrue(it in fileSerializersByUrl[getNonNullActualFileUrl(it.internalEntitySource)])
  }

  fun <E : TypedEntity> isSerializerWithoutEntities(serializer: JpsFileEntitiesSerializer<E>) =
    serializer is JpsFileEntityTypeSerializer<E> && storage.entities(serializer.mainEntityClass).none { serializer.entityFilter(it) }

  val allSources = storage.entitiesBySource { true }
  val urlsFromSources = allSources.keys.filterIsInstance<JpsFileEntitySource>().mapTo(HashSet()) { getNonNullActualFileUrl(it) }
  assertEquals(urlsFromSources.sorted(), fileSerializersByUrl.entrySet().filterNot { it.value.all { isSerializerWithoutEntities(it)} }.map { it.key }.sorted())

  val fileIdFromEntities = allSources.keys.filterIsInstance(JpsFileEntitySource.FileInDirectory::class.java).mapTo(HashSet()) { it.fileNameId }
  val unregisteredIds = fileIdFromEntities - fileIdToFileName.keys().toSet()
  assertTrue("Some fileNameId aren't registered: ${unregisteredIds}", unregisteredIds.isEmpty())
  val staleIds = fileIdToFileName.keys().toSet() - fileIdFromEntities
  assertTrue("There are stale mapping for some fileNameId: ${staleIds.joinToString { "$it -> ${fileIdToFileName.get(it)}" }}", staleIds.isEmpty())
}

internal fun File.asConfigLocation(virtualFileManager: VirtualFileUrlManager): JpsProjectConfigLocation =
  if (FileUtil.extensionEquals(name, "ipr")) JpsProjectConfigLocation.FileBased(toVirtualFileUrl(virtualFileManager))
  else JpsProjectConfigLocation.DirectoryBased(toVirtualFileUrl(virtualFileManager))

internal class JpsFileContentWriterImpl : JpsFileContentWriter {
  val urlToComponents = LinkedHashMap<String, LinkedHashMap<String, Element?>>()

  override fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?) {
    urlToComponents.computeIfAbsent(fileUrl) { LinkedHashMap() }[componentName] = componentTag
  }
}