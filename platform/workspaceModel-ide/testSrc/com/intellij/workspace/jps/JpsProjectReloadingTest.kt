package com.intellij.workspace.jps

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.workspace.api.ModuleEntity
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.projectLibraries
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Test
import java.io.File

class JpsProjectReloadingTest : HeavyPlatformTestCase() {
  @Test
  fun `test modify iml`() {
    checkProjectAfterReload("common/modifyIml", "common/modifyIml") { (storage, projectDirUrl) ->
      val modules = storage.entities(ModuleEntity::class).sortedBy { it.name }.toList()
      assertEquals(3, modules.size)
      val utilModule = modules[1]
      assertEquals("util", utilModule.name)
      val utilModuleSrc = assertOneElement(utilModule.sourceRoots.toList())
      assertEquals("$projectDirUrl/util/src2", utilModuleSrc.url.url)
      assertEquals("""<component LANGUAGE_LEVEL="JDK_1_7">
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
  fun `test add library`() {
    checkProjectAfterReload( "directoryBased/addLibrary", "fileBased/addLibrary") { (storage, projectDirUrl) ->
      val libraries = storage.projectLibraries.sortedBy { it.name }.toList()
      assertEquals(4, libraries.size)
      val junitLibrary = libraries[2]
      assertEquals("junit2", junitLibrary.name)
      val root = assertOneElement(junitLibrary.roots.toList())
      assertEquals("jar://${JpsPathUtil.urlToPath(projectDirUrl)}/lib/junit2.jar!/", root.url.url)
    }
  }

  fun `test add module`() {
    checkProjectAfterReload("directoryBased/addModule", "fileBased/addModule") { (storage, projectDirUrl) ->
      val modules = storage.entities(ModuleEntity::class).sortedBy { it.name }.toList()
      assertEquals(4, modules.size)
      val newModule = modules[1]
      assertEquals("newModule", newModule.name)
      val root = assertOneElement(newModule.sourceRoots.toList())
      assertEquals("$projectDirUrl/new", root.url.url)
    }
  }

  fun `test remove module`() {
    checkProjectAfterReload("directoryBased/removeModule", "fileBased/removeModule") { (storage, _) ->
      assertEquals(setOf("main", "xxx"), storage.entities(ModuleEntity::class).mapTo(HashSet()) {it.name})
    }
  }

  @Test
  fun `test modify library`() {
    checkProjectAfterReload("directoryBased/modifyLibrary", "fileBased/modifyLibrary") { (storage, projectDirUrl) ->
      val libraries = storage.projectLibraries.sortedBy { it.name }.toList()
      assertEquals(3, libraries.size)
      val junitLibrary = libraries[1]
      assertEquals("junit", junitLibrary.name)
      val root = assertOneElement(junitLibrary.roots.toList())
      assertEquals("jar://${JpsPathUtil.urlToPath(projectDirUrl)}/lib/junit2.jar!/", root.url.url)
    }
  }

  @Test
  fun `test remove library in directory-based project`() {
    val (storage, _) = reload(sampleDirBasedProjectFile) { data ->
      FileUtil.delete(File(data.projectDir, ".idea/libraries/junit.xml"))
      JpsConfigurationFilesChange(addedFileUrls = emptyList(),
                                  removedFileUrls = listOf("${data.projectDirUrl}/.idea/libraries/junit.xml"),
                                  changedFileUrls = emptyList())
    }
    assertEquals(setOf("jarDir", "log4j"), storage.projectLibraries.mapTo(HashSet()) {it.name})
  }

  @Test
  fun `test remove library in file-based project`() {
    val (storage, _) = reload(sampleFileBasedProjectFile, "fileBased/removeLibrary")
    assertEquals(setOf("jarDir", "log4j"), storage.projectLibraries.mapTo(HashSet()) {it.name})
  }

  private fun checkProjectAfterReload(directoryNameForDirectoryBased: String, directoryNameForFileBased: String, checkAction: (ReloadedProjectData) -> Unit) {
    val dirBasedData = reload(sampleDirBasedProjectFile, directoryNameForDirectoryBased)
    checkAction(dirBasedData)
    val fileBasedData = reload(sampleFileBasedProjectFile, directoryNameForFileBased)
    checkAction(fileBasedData)
  }

  private fun reload(originalProjectFile: File,
                     updateAction: (LoadedProjectData) -> JpsConfigurationFilesChange): ReloadedProjectData {
    val projectData = copyAndLoadProject(originalProjectFile)
    val change = updateAction(projectData)
    val (changedEntities, builder) = projectData.serializationData.reloadFromChangedFiles(change, CachingJpsFileContentReader(projectData.projectDirUrl))
    val originalBuilder = TypedEntityStorageBuilder.from(projectData.storage)
    originalBuilder.replaceBySource({it in changedEntities}, builder)
    projectData.serializationData.checkConsistency(projectData.projectDirUrl, originalBuilder)
    return ReloadedProjectData(originalBuilder, projectData.projectDirUrl)
  }

  private fun reload(originalProjectDir: File, directoryName: String): ReloadedProjectData {
    return reload(originalProjectDir) { projectData ->
      val changedDir = PathManagerEx.findFileUnderCommunityHome("platform/workspaceModel-ide/testData/serialization/reload/$directoryName")
      val newUrls = collectFileUrls(changedDir, projectData.projectDirUrl)
      val oldUrls = collectFileUrls(projectData.projectDir, projectData.projectDirUrl)
      FileUtil.copyDir(changedDir, projectData.projectDir)
      JpsConfigurationFilesChange(addedFileUrls = newUrls - oldUrls, removedFileUrls = emptyList(),
                                  changedFileUrls = newUrls.intersect(oldUrls).toList())
    }
  }

  private fun collectFileUrls(dir: File, baseUrl: String): ArrayList<String> {
    return dir.walkTopDown().mapTo(ArrayList()) {
      baseUrl + "/${FileUtil.toSystemIndependentName(FileUtil.getRelativePath(dir, it)!!)}"
    }
  }

  private data class ReloadedProjectData(val storage: TypedEntityStorageBuilder, val projectDirUrl: String)
}