package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.impl.UnloadedModulesNameHolderImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.workspace.jps.UnloadedModulesNameHolder
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.customImlData
import com.intellij.platform.workspace.jps.entities.projectLibraries
import com.intellij.platform.workspace.jps.entities.sourceRoots
import com.intellij.platform.workspace.jps.serialization.impl.JpsConfigurationFilesChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

@TestApplication
class JpsProjectReloadingTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @BeforeEach
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @ParameterizedTest(name = "unloaded = {0}")
  @ValueSource(strings = ["", "util", "util,main", "main"])
  fun `modify iml`(unloaded: String) {
    val unloadedModuleNames = StringUtil.split(unloaded, ",").toSet()
    val unloadedHolder = UnloadedModulesNameHolderImpl(unloadedModuleNames)
    checkProjectAfterReload("common/modifyIml", "common/modifyIml", unloadedHolder) { (storage, unloadedEntityStorage, projectDirUrl) ->
      val modules = storage.entities(ModuleEntity::class.java).sortedBy { it.name }.toList()
      assertEquals(3 - unloadedModuleNames.size, modules.size)
      val unloadedModules = unloadedEntityStorage.entities(ModuleEntity::class.java).toList()
      assertEquals(unloadedModuleNames, unloadedModules.mapTo(HashSet()) { it.name })
      val utilModule = (modules + unloadedModules).find { it.name == "util" }!!
      assertEquals("util", utilModule.name)
      val utilModuleSrc = assertOneElement(utilModule.sourceRoots.toList())
      assertEquals("$projectDirUrl/util/src2", utilModuleSrc.url.url)
      assertEquals("""<component>
  <annotation-paths>
    <root url="$projectDirUrl/lib/anno2" />
  </annotation-paths>
  <javadoc-paths>
    <root url="$projectDirUrl/lib/javadoc2" />
  </javadoc-paths>
</component>""", utilModule.customImlData!!.rootManagerTagCustomData)
    }
  }

  @Test
  fun `add library`() {
    checkProjectAfterReload("directoryBased/addLibrary", "fileBased/addLibrary") { (storage, _, projectDirUrl) ->
      val libraries = storage.projectLibraries.sortedBy { it.name }.toList()
      assertEquals(4, libraries.size)
      val junitLibrary = libraries[2]
      assertEquals("junit2", junitLibrary.name)
      val root = assertOneElement(junitLibrary.roots.toList())
      assertEquals("jar://${JpsPathUtil.urlToPath(projectDirUrl)}/lib/junit2.jar!/", root.url.url)
    }
  }

  @Test
  fun `all libraries for directory based project`() {
    val projectDir = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/imlUnderDotIdea")
    val (storage, _, projectDirUrl) = reload(projectDir, "directoryBased/addLibrary", UnloadedModulesNameHolder.DUMMY)

    val libraries = storage.projectLibraries.sortedBy { it.name }.toList()
    assertEquals(1, libraries.size)
    val junitLibrary = libraries[0]
    assertEquals("junit2", junitLibrary.name)
    val root = assertOneElement(junitLibrary.roots.toList())
    assertEquals("jar://${JpsPathUtil.urlToPath(projectDirUrl)}/lib/junit2.jar!/", root.url.url)
  }

  @ParameterizedTest(name = "unloaded = {0}")
  @ValueSource(strings = ["", "newModule", "newModule,main", "main"])
  fun `add module`(unloaded: String) {
    val unloadedModuleNames = StringUtil.split(unloaded, ",").toSet()
    val unloadedHolder = UnloadedModulesNameHolderImpl(unloadedModuleNames)
    checkProjectAfterReload("directoryBased/addModule", "fileBased/addModule",
                            unloadedHolder) { (storage, unloadedEntitiesStorage, projectDirUrl) ->
      val modules = storage.entities(ModuleEntity::class.java).sortedBy { it.name }.toList()
      assertEquals(4 - unloadedModuleNames.size, modules.size)
      val unloadedModules = unloadedEntitiesStorage.entities(ModuleEntity::class.java)
      assertEquals(unloadedModuleNames, unloadedModules.map { it.name }.toSet())
      val newModule = (modules + unloadedModules).find { it.name == "newModule" }!!
      assertEquals("newModule", newModule.name)
      val root = assertOneElement(newModule.sourceRoots.toList())
      assertEquals("$projectDirUrl/new", root.url.url)
    }
  }

  @Test
  fun `remove add iml file`() {
    checkProjectAfterReload(listOf("common/removeImlFile", "common/addImlFile"),
                            listOf("common/removeImlFile", "common/addImlFile"))
    { (storage, _, projectDirUrl) ->
      val modules = storage.entities(ModuleEntity::class.java).sortedBy { it.name }.toList()
      assertEquals(3, modules.size)
      val mainModule = modules.find { it.name == "main" }!!
      val root = assertOneElement(mainModule.sourceRoots.toList())
      assertEquals("$projectDirUrl/src", root.url.url)
      assertEquals("$projectDirUrl/src", root.url.url)
    }
  }

  @ParameterizedTest(name = "unloaded = {0}")
  @ValueSource(strings = ["", "util", "util,main", "main"])
  fun `remove module`(unloaded: String) {
    val unloadedModuleNames = StringUtil.split(unloaded, ",").toSet()
    val unloadedHolder = UnloadedModulesNameHolderImpl(unloadedModuleNames)
    checkProjectAfterReload("directoryBased/removeModule", "fileBased/removeModule",
                            unloadedHolder) { (storage, unloadedEntitiesStorage, _) ->
      assertEquals(unloadedModuleNames - "util", unloadedEntitiesStorage.entities(ModuleEntity::class.java).mapTo(HashSet()) { it.name })
      val allEntities = storage.entities(ModuleEntity::class.java) + unloadedEntitiesStorage.entities(ModuleEntity::class.java)
      assertEquals(setOf("main", "xxx"), allEntities.mapTo(HashSet()) { it.name })
    }
  }

  @Test
  fun `modify library`() {
    checkProjectAfterReload("directoryBased/modifyLibrary",
                            "fileBased/modifyLibrary") { (storage, _, projectDirUrl) ->
      val libraries = storage.projectLibraries.sortedBy { it.name }.toList()
      assertEquals(3, libraries.size)
      val junitLibrary = libraries[1]
      assertEquals("junit", junitLibrary.name)
      val root = assertOneElement(junitLibrary.roots.toList())
      assertEquals("jar://${JpsPathUtil.urlToPath(projectDirUrl)}/lib/junit2.jar!/", root.url.url)
    }
  }

  @Test
  fun `remove library`() {
    checkProjectAfterReload("directoryBased/removeLibrary", "fileBased/removeLibrary") { (storage, _, _) ->
      assertEquals(setOf("jarDir", "log4j"), storage.projectLibraries.mapTo(HashSet()) { it.name })
    }
  }

  @Test
  fun `remove all libraries`() {
    checkProjectAfterReload("directoryBased/removeAllLibraries",
                            "fileBased/removeAllLibraries") { (storage, _, _) ->
      assertEquals(emptySet<String>(), storage.projectLibraries.mapTo(HashSet()) { it.name })
    }
  }

  private fun checkProjectAfterReload(
    directoryNameForDirectoryBased: List<String>,
    directoryNameForFileBased: List<String>,
    unloadedModulesNameHolder: UnloadedModulesNameHolder = UnloadedModulesNameHolder.DUMMY,
    checkAction: (ReloadedProjectData) -> Unit,
  ) {
    val dirBasedData = reload(sampleDirBasedProjectFile, directoryNameForDirectoryBased, unloadedModulesNameHolder)
    checkAction(dirBasedData)
    val fileBasedData = reload(sampleFileBasedProjectFile, directoryNameForFileBased, unloadedModulesNameHolder)
    checkAction(fileBasedData)
  }

  private fun checkProjectAfterReload(
    directoryNameForDirectoryBased: String,
    directoryNameForFileBased: String,
    unloadedModulesNameHolder: UnloadedModulesNameHolder = UnloadedModulesNameHolder.DUMMY,
    checkAction: (ReloadedProjectData) -> Unit,
  ) {
    val dirBasedData = reload(sampleDirBasedProjectFile, directoryNameForDirectoryBased, unloadedModulesNameHolder)
    checkAction(dirBasedData)
    val fileBasedData = reload(sampleFileBasedProjectFile, directoryNameForFileBased, unloadedModulesNameHolder)
    checkAction(fileBasedData)
  }

  private fun reload(
    originalProjectFile: File,
    unloadedModulesNameHolder: UnloadedModulesNameHolder,
    updateActions: List<(LoadedProjectData) -> JpsConfigurationFilesChange>,
  ): ReloadedProjectData {
    Assertions.assertFalse(updateActions.isEmpty())
    val projectData = copyAndLoadProject(originalProjectFile, virtualFileManager, unloadedModulesNameHolder)
    val originalBuilder = MutableEntityStorage.from(projectData.storage)
    val originalUnloadedEntitiesBuilder = MutableEntityStorage.from(projectData.unloadedEntitiesStorage)
    val projectConfigLocation = projectData.configLocation
    val projectDirUrl = projectData.projectDirUrl
    val serializers = projectData.serializers

    lateinit var latestResult: ReloadedProjectData
    for (updateAction in updateActions) {
      val change = updateAction(projectData)
      val result = serializers.reloadFromChangedFiles(change, CachingJpsFileContentReader(projectConfigLocation),
                                                      unloadedModulesNameHolder, TestErrorReporter)
      originalBuilder.replaceBySource({ it in result.affectedSources }, result.builder)
      originalUnloadedEntitiesBuilder.replaceBySource({ it in result.affectedSources }, result.unloadedEntityBuilder)
      serializers.checkConsistency(projectConfigLocation, originalBuilder, originalUnloadedEntitiesBuilder,
                                               virtualFileManager)
      latestResult = ReloadedProjectData(originalBuilder, originalUnloadedEntitiesBuilder, projectDirUrl)
    }
    return latestResult
  }

  private fun reload(
    originalProjectDir: File,
    directoryName: String,
    unloadedModulesNameHolder: UnloadedModulesNameHolder,
  ): ReloadedProjectData {
    return reload(originalProjectDir, listOf(directoryName), unloadedModulesNameHolder)
  }

  private fun reload(
    originalProjectDir: File,
    directoryNames: List<String>,
    unloadedModulesNameHolder: UnloadedModulesNameHolder,
  ): ReloadedProjectData {
    val actions: List<(LoadedProjectData) -> JpsConfigurationFilesChange> = directoryNames.map { directoryName ->
      { projectData ->
        val changedDir = PathManagerEx.findFileUnderCommunityHome(
          "platform/workspace/jps/tests/testData/serialization/reload/$directoryName")
        val newUrls = collectFileUrlsRec(changedDir, projectData.projectDirUrl, true) { it != "<delete/>" }
        val urlsToDelete = collectFileUrlsRec(changedDir, projectData.projectDirUrl, true) { it == "<delete/>" }
        val oldUrls = collectFileUrlsRec(projectData.projectDir, projectData.projectDirUrl, false) { true }
        FileUtil.copyDir(changedDir, projectData.projectDir)
        projectData.projectDir.walk().filter { it.isFile && it.readText().trim() == "<delete/>" }.forEach {
          FileUtil.delete(it)
        }

        JpsConfigurationFilesChange(addedFileUrls = newUrls - oldUrls, removedFileUrls = urlsToDelete,
                                    changedFileUrls = newUrls.intersect(oldUrls).toList())
      }
    }

    return reload(originalProjectDir, unloadedModulesNameHolder, actions)
  }

  private fun collectFileUrlsRec(dir: File,
                                 baseUrl: String,
                                 replaceByParent: Boolean,
                                 contentFilter: (String) -> Boolean): Set<String> {
    val res = HashSet<String>()

    dir.walkBottomUp().forEach { file ->
      if (file.isFile && contentFilter(file.readText().trim())) {
        res += replaceBaseUrl(baseUrl, dir, file)
      }
      else if (file.isDirectory) {
        // If all removed files relate to the one directory, the directory event is sent
        val relativeFileUrl = FileUtil.getRelativePath(dir, file) ?: return@forEach
        if ("." == relativeFileUrl) return@forEach
        if (!relativeFileUrl.endsWith(".idea/libraries") && !relativeFileUrl.endsWith(".idea/artifacts")) return@forEach

        // Existing files + files that will be copied
        val children = (JpsPathUtil.urlToFile(replaceBaseUrl(baseUrl, dir, file)).listFiles()?.map {
          JpsPathUtil.pathToUrl(it.absolutePath)
        } ?: emptyList()) +
                       (file.listFiles()?.map { replaceBaseUrl(baseUrl, dir, it) } ?: emptyList())
        if (children.all { it in res }) {
          if (replaceByParent) children.forEach { res.remove(it) }
          res += replaceBaseUrl(baseUrl, dir, file)
        }
      }
    }

    return res
  }

  private fun replaceBaseUrl(newBaseUrl: String, oldBaseUrl: File, file: File): String {
    return "$newBaseUrl/${FileUtil.toSystemIndependentName(FileUtil.getRelativePath(oldBaseUrl, file)!!)}"
  }

  private data class ReloadedProjectData(val storage: MutableEntityStorage,
                                         val unloadedEntitiesStorage: MutableEntityStorage,
                                         val projectDirUrl: String)
}
