// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.configurationStore.StoreUtil.saveDocumentsAndProjectsAndApp
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.components.PathMacroMap
import com.intellij.openapi.components.impl.ModulePathMacroManager
import com.intellij.openapi.components.impl.ProjectPathMacroManager
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.module.impl.UnloadedModulesNameHolderImpl
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.workspace.jps.*
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.serialization.impl.*
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.LineSeparator
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import com.intellij.workspaceModel.ide.JpsGlobalModelSynchronizer
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModelRegistry
import junit.framework.AssertionFailedError
import kotlinx.coroutines.CoroutineScope
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.function.Supplier
import kotlin.coroutines.EmptyCoroutineContext

internal val sampleDirBasedProjectFile = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
internal val sampleFileBasedProjectFile = File(PathManagerEx.getCommunityHomePath(),
                                               "jps/model-serialization/testData/sampleProject-ipr/sampleProject.ipr")

internal data class LoadedProjectData(
  val storage: ImmutableEntityStorage,
  val orphanage: ImmutableEntityStorage,
  val unloadedEntitiesStorage: ImmutableEntityStorage,
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
                                unloadedModuleNameHolder: UnloadedModulesNameHolder = UnloadedModulesNameHolder.DUMMY,
                                checkConsistencyAfterLoading: Boolean = true,
                                externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null): LoadedProjectData {
  val (projectDir, originalProjectDir) = copyProjectFiles(originalProjectFile)
  val originalBuilder = MutableEntityStorage.create()
  val projectFile = if (originalProjectFile.isFile) File(projectDir, originalProjectFile.name) else projectDir
  val configLocation = toConfigLocation(projectFile.toPath(), virtualFileManager)
  val unloadedEntitiesBuilder = MutableEntityStorage.create()
  val orphanage = MutableEntityStorage.create()
  val serializers = loadProject(configLocation, originalBuilder, orphanage, virtualFileManager,
                                externalStorageConfigurationManager = externalStorageConfigurationManager,
                                unloadedModuleNameHolder = unloadedModuleNameHolder,
                                unloadedEntitiesBuilder = unloadedEntitiesBuilder) as JpsProjectSerializersImpl
  val loadedProjectData = LoadedProjectData(originalBuilder.toSnapshot(), orphanage.toSnapshot(), unloadedEntitiesBuilder.toSnapshot(),
                                            serializers, configLocation,
                                            originalProjectDir)
  if (checkConsistencyAfterLoading) {
    serializers.checkConsistency(loadedProjectData.configLocation, loadedProjectData.storage, loadedProjectData.unloadedEntitiesStorage,
                                 virtualFileManager)
  }
  return loadedProjectData
}

internal fun copyProjectFiles(originalProjectFile: File): Pair<File, File> {
  val projectDir = Files.createTempDirectory("jpsProjectTest").toFile()
  val originalProjectDir = if (originalProjectFile.isFile) originalProjectFile.parentFile else originalProjectFile
  FileUtil.copyDir(originalProjectDir, projectDir)
  return projectDir to originalProjectDir
}

internal fun loadProject(configLocation: JpsProjectConfigLocation,
                         originalBuilder: MutableEntityStorage,
                         orphanage: MutableEntityStorage,
                         virtualFileManager: VirtualFileUrlManager,
                         unloadedModuleNameHolder: UnloadedModulesNameHolder = UnloadedModulesNameHolder.DUMMY,
                         unloadedEntitiesBuilder: MutableEntityStorage = MutableEntityStorage.create(),
                         fileInDirectorySourceNames: FileInDirectorySourceNames = FileInDirectorySourceNames.empty(),
                         externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null,
                         errorReporter: ErrorReporter = TestErrorReporter): JpsProjectSerializers {
  val cacheDirUrl = configLocation.baseDirectoryUrl.append("cache")
  val isExternalStorageEnabled = externalStorageConfigurationManager != null && externalStorageConfigurationManager.isEnabled
  val context = SerializationContextForTests(virtualFileManager, CachingJpsFileContentReader(configLocation), isExternalStorageEnabled,
                                             fileInDirectorySourceNames)
  return runUnderModalProgressIfIsEdt {
    JpsProjectEntitiesLoader.loadProject(configLocation,
                                         originalBuilder,
                                         orphanage,
                                         File(VfsUtil.urlToPath(cacheDirUrl.url)).toPath(),
                                         errorReporter,
                                         unloadedModuleNameHolder,
                                         unloadedEntitiesBuilder,
                                         context)
  }
}

@TestOnly
fun JpsProjectSerializersImpl.saveAllEntities(storage: EntityStorage, configLocation: JpsProjectConfigLocation) {
  val writer = JpsFileContentWriterImpl(configLocation)
  saveAllEntities(storage, writer)
  val modulePathMapping = this.moduleSerializers.keys.filterIsInstance<ExternalModuleImlFileEntitiesSerializer>()
    .associate { it.fileUrl.url to it.modulePath.path }
  writer.writeFiles(modulePathMapping)
}

@TestOnly
fun JpsProjectSerializersImpl.saveAffectedEntities(storage: EntityStorage, affectedEntitySources: Set<EntitySource>, configLocation: JpsProjectConfigLocation) {
  val writer = JpsFileContentWriterImpl(configLocation)
  saveAffectedEntities(storage, affectedEntitySources, writer)
  val modulePathMapping = this.moduleSerializers.keys.filterIsInstance<ExternalModuleImlFileEntitiesSerializer>()
    .associate { it.fileUrl.url to it.modulePath.path }
  writer.writeFiles(modulePathMapping)
}

internal fun assertDirectoryMatches(actualDir: File, expectedDir: File, filesToIgnore: Set<String>, componentsToIgnore: List<String>) {
  val actualFiles = actualDir.walk().filter { it.isFile }.associateBy {
    FileUtil.toSystemIndependentName(FileUtil.getRelativePath(actualDir, it)!!)
  }
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

internal fun createProjectSerializers(
  projectDir: File,
  virtualFileManager: VirtualFileUrlManager,
  externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null
): Pair<JpsProjectSerializersImpl, JpsProjectConfigLocation> {
  val configLocation = toConfigLocation(projectDir.toPath(), virtualFileManager)
  val reader = CachingJpsFileContentReader(configLocation)
  val externalStoragePath = projectDir.toPath().resolve("cache")
  val isExternalStorageEnabled = externalStorageConfigurationManager != null && externalStorageConfigurationManager.isEnabled
  val context = SerializationContextForTests(virtualFileManager, reader, isExternalStorageEnabled)
  val serializer = JpsProjectEntitiesLoader.createProjectSerializers(configLocation, externalStoragePath,
                                                                     context) as JpsProjectSerializersImpl
  return serializer to configLocation
}

fun JpsProjectSerializersImpl.checkConsistency(configLocation: JpsProjectConfigLocation,
                                               storage: EntityStorage,
                                               unloadedEntitiesStorage: EntityStorage,
                                               virtualFileManager: VirtualFileUrlManager) {
  fun getNonNullActualFileUrl(source: EntitySource): String {
    return getActualFileUrl(source) ?: throw AssertionFailedError("file name is not registered for $source")
  }

  directorySerializerFactoriesByUrl.forEach { (url, directorySerializer) ->
    assertEquals(url, directorySerializer.directoryUrl)
    val fileSerializers = serializerToDirectoryFactory.getKeysByValue(directorySerializer) ?: emptyList()
    val directoryFileUrls = JpsPathUtil.urlToFile(url).listFiles { file: File -> file.isFile }?.map {
      JpsPathUtil.pathToUrl(it.systemIndependentPath)
    } ?: emptyList()
    assertEquals(directoryFileUrls.sorted(), fileSerializers.map { getNonNullActualFileUrl(it.internalEntitySource) }.sorted())
  }

  moduleListSerializersByUrl.forEach { (url, fileSerializer) ->
    assertEquals(url, fileSerializer.fileUrl)
    val fileSerializers = moduleSerializers.getKeysByValue(fileSerializer) ?: emptyList()
    val urlsFromFactory = fileSerializer.loadFileList(CachingJpsFileContentReader(configLocation), virtualFileManager)
    assertEquals(urlsFromFactory.map { it.first.url }.sorted(),
                 fileSerializers.map { getNonNullActualFileUrl(it.internalEntitySource) }.sorted())
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
    && unloadedEntitiesStorage.entities(serializer.mainEntityClass).none { serializer.entityFilter(it) }

  val allSources = storage.entitiesBySource { true }.mapTo(HashSet()) { it.entitySource } +
                   unloadedEntitiesStorage.entitiesBySource { true }.mapTo(HashSet()) { it.entitySource }
  val urlsFromSources = allSources
    .filterIsInstance<JpsFileEntitySource>()
    .filterNot { it is JpsGlobalFileEntitySource } // Do not check global entity sources in project-level serializers
    .mapTo(HashSet()) { getNonNullActualFileUrl(it) }

  // iml file can be declared in modules.xml, but there might be no the file itself on the disk. Not sure if we should consider this
  // situation as an empty module declaration, or no module at all (at the moment we consider this as a no-module).
  // Either way, we have a serializer (because ModuleListSerializer creates serializers based on what it sees in the modules.xml
  // file, even if the iml file does not exist on the disk). This should be taken into account during validation.
  val moduleSerializersWithoutFiles = fileSerializersByUrl.values
    .mapNotNull { it as? ModuleImlFileEntitiesSerializer }
    .filter { !Files.exists(Paths.get(it.modulePath.path)) }
    .toSet()
  val moduleFileNamesWithoutFiles = moduleSerializersWithoutFiles.map { it.modulePath.moduleName + ".iml" }.toSet()
  val moduleUrlsWithoutFiles = moduleSerializersWithoutFiles.map { it.fileUrl.url }.toSet()

  // module without iml file is kind of a serializer without entity, but when WSM is loaded from cache, we can find ourselves in a situation
  // that file on the disk does not exist already, but entities loaded from that file still exist. I.e. urlsFromSources
  // may (when the model loaded from cache) or may not (when the model loaded from files) contain non-existing module urls.
  assertEquals((urlsFromSources + moduleUrlsWithoutFiles).sorted(), fileSerializersByUrl.keys.associateWith { fileSerializersByUrl.getValues(it) }
    .filterNot { entry -> entry.value.all { isSerializerWithoutEntities(it) } }
    .map { it.key }.sorted())

  val fileIdFromEntities = allSources.filterIsInstance<JpsProjectFileEntitySource.FileInDirectory>().mapTo(
    HashSet()) { it.fileNameId }
  val unregisteredIds = fileIdFromEntities - fileIdToFileName.keys.toSet()
  assertTrue("Some fileNameId aren't registered: ${unregisteredIds}", unregisteredIds.isEmpty())
  val staleIds = (fileIdToFileName.keys.toSet() - fileIdFromEntities).toMutableSet()
  staleIds.removeIf { moduleFileNamesWithoutFiles.contains(fileIdToFileName[it]) } // module without iml file not producing a stale file id
  assertTrue("There are stale mapping for some fileNameId: ${staleIds.joinToString { "$it -> ${fileIdToFileName.get(it)}" }}",
             staleIds.isEmpty())
}

internal fun File.asConfigLocation(virtualFileManager: VirtualFileUrlManager): JpsProjectConfigLocation = toConfigLocation(toPath(),
                                                                                                                           virtualFileManager)

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

  internal fun writeFiles(modulePathMapping: Map<String, String>) {
    urlToComponents.forEach { (url, newComponents) ->
      val components = HashMap(newComponents)
      val file = JpsPathUtil.urlToFile(url)
      val replaceMacroMap = getReplacePathMacroMap(modulePathMapping[url] ?: url)
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

internal object SilentErrorReporter : ErrorReporter {
  override fun reportError(message: String, file: VirtualFileUrl) {
    // Nothing here
  }
}

internal class CollectingErrorReporter : ErrorReporter {
  val messages = ArrayList<String>()
  override fun reportError(message: String, file: VirtualFileUrl) {
    RuntimeException(message).printStackTrace()
    messages += message
  }
}

internal fun checkSaveProjectAfterChange(originalProjectFile: File,
                                         changedFilesDirectoryName: String?,
                                         change: (MutableEntityStorage, MutableEntityStorage, MutableEntityStorage, JpsProjectConfigLocation) -> Unit,
                                         unloadedModuleNameHolder: UnloadedModulesNameHolder = UnloadedModulesNameHolder.DUMMY,
                                         virtualFileManager: VirtualFileUrlManager,
                                         testDir: String,
                                         checkConsistencyAfterLoading: Boolean = true,
                                         externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null,
                                         forceAllFilesRewrite: Boolean = false) {
  val projectData = copyAndLoadProject(originalProjectFile, virtualFileManager, unloadedModuleNameHolder, checkConsistencyAfterLoading,
                                       externalStorageConfigurationManager)
  val builder = MutableEntityStorage.from(projectData.storage) as MutableEntityStorageInstrumentation
  val unloadedEntitiesBuilder = MutableEntityStorage.from(projectData.unloadedEntitiesStorage) as MutableEntityStorageInstrumentation
  change(builder, projectData.orphanage.toBuilder(), unloadedEntitiesBuilder, projectData.configLocation)
  val changesList = builder.collectChanges().values + unloadedEntitiesBuilder.collectChanges().values
  val changedSources = changesList.flatMapTo(HashSet()) { changes ->
    changes.flatMap { change ->
      when (change) {
        is EntityChange.Added -> listOf(change.newEntity)
        is EntityChange.Removed -> listOf(change.oldEntity)
        is EntityChange.Replaced -> listOf(change.oldEntity, change.newEntity)
      }
    }.map { it.entitySource }
  }
  if (forceAllFilesRewrite) {
    changedSources.addAll(builder.entitiesBySource { true }.mapTo(HashSet()) { it.entitySource })
  }
  val writer = JpsFileContentWriterImpl(projectData.configLocation)
  projectData.serializers.saveEntities(builder.toSnapshot(), unloadedEntitiesBuilder.toSnapshot(), changedSources, writer)
  val modulePathMapping = projectData.serializers.moduleSerializers.keys.filterIsInstance<ExternalModuleImlFileEntitiesSerializer>()
    .associate { it.fileUrl.url to it.modulePath.path }
  writer.writeFiles(modulePathMapping)
  if (checkConsistencyAfterLoading) {
    projectData.serializers.checkConsistency(projectData.configLocation, builder.toSnapshot(), unloadedEntitiesBuilder.toSnapshot(),
                                             virtualFileManager)
  }

  val expectedDir = FileUtil.createTempDirectory("jpsProjectTest", "expected")
  FileUtil.copyDir(projectData.originalProjectDir, expectedDir)
  if (changedFilesDirectoryName != null) {
    val changedDir = PathManagerEx.findFileUnderCommunityHome(
      "platform/workspace/jps/tests/testData/$testDir/$changedFilesDirectoryName")
    FileUtil.copyDir(changedDir, expectedDir)
  }
  expectedDir.walk().filter { it.isFile && it.readText().trim() == "<delete/>" }.forEach {
    FileUtil.delete(it)
  }

  assertDirectoryMatches(projectData.projectDir, expectedDir, emptySet(), emptyList())
}

internal fun copyAndLoadGlobalEntities(originalFile: String? = null,
                                       expectedFile: String? = null,
                                       testDir: File,
                                       parentDisposable: Disposable,
                                       action: (JpsGlobalFileEntitySource, JpsGlobalFileEntitySource) -> Unit) {
  val stateStore = ApplicationManager.getApplication().stateStore
  val oldConfigDir = PathManager.getConfigDir()
  try {
    PathManager.setExplicitConfigPath(testDir.toPath())
    stateStore.setPath(testDir.toPath())
    stateStore.clearCaches()

    val optionsFolder = testDir.resolve("options")
    JpsGlobalModelSynchronizerImpl.runWithGlobalEntitiesLoadingEnabled {
      // Copy original file before loading
      if (originalFile != null) {
        val globalEntitiesFolder = File(PathManagerEx.getCommunityHomePath(),
                                        "platform/workspace/jps/tests/testData/serialization/global/$originalFile")
        FileUtil.copyDir(globalEntitiesFolder, optionsFolder)
      }

      // Reinitialize application level services
      val application = ApplicationManager.getApplication()
      val coroutineScope = CoroutineScope(EmptyCoroutineContext)
      ApplicationManager.getApplication().replaceService(JpsGlobalModelSynchronizer::class.java,
                                                         JpsGlobalModelSynchronizerImpl(coroutineScope),
                                                         parentDisposable)
      ApplicationManager.getApplication().replaceService(GlobalWorkspaceModelRegistry::class.java, GlobalWorkspaceModelRegistry(), parentDisposable)

      // Entity source for global entities
      val virtualFileManager = GlobalWorkspaceModel.getInstance(LocalEelMachine).getVirtualFileUrlManager()
      val globalLibrariesFile = virtualFileManager.getOrCreateFromUrl("$testDir/options/applicationLibraries.xml")
      val libraryEntitySource = JpsGlobalFileEntitySource(globalLibrariesFile)

      val globalSdkFile = virtualFileManager.getOrCreateFromUrl("$testDir/options/jdk.table.xml")
      val sdkEntitySource = JpsGlobalFileEntitySource(globalSdkFile)
      action(libraryEntitySource, sdkEntitySource)

      // Save current state and check it's expected
      if (expectedFile != null) {
        application.invokeAndWait { saveDocumentsAndProjectsAndApp(true) }
        val globalEntitiesFolder = File(PathManagerEx.getCommunityHomePath(),
                                        "platform/workspace/jps/tests/testData/serialization/global/$expectedFile")
        val entityStorage = GlobalWorkspaceModel.getInstance(LocalEelMachine).entityStorage.current
        if (entityStorage.entities(LibraryEntity::class.java).toList().isEmpty()) {
          optionsFolder.assertMatches(directoryContentOf(globalEntitiesFolder.toPath()),
                                      filePathFilter = { it.contains("jdk.table.xml") })
        }
        else {
          optionsFolder.assertMatches(directoryContentOf(globalEntitiesFolder.toPath()),
                                      filePathFilter = { it.contains("applicationLibraries.xml") })
        }
      }
    }
  }
  finally {
    PathManager.setExplicitConfigPath(oldConfigDir)
    stateStore.setPath(oldConfigDir)
    stateStore.clearCaches()
  }
}

internal fun unloadedHolder(unloaded: String): UnloadedModulesNameHolder {
  val unloadedModuleNames = StringUtil.split(unloaded, ",").toSet()
  return UnloadedModulesNameHolderImpl(unloadedModuleNames)
}
