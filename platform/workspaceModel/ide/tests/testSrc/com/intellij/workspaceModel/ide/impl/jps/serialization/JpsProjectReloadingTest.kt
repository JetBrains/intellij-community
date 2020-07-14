package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.VirtualFileUrlManager
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.projectLibraries
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File

class JpsProjectReloadingTest : HeavyPlatformTestCase() {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  override fun setUp() {
    super.setUp()
    virtualFileManager = VirtualFileUrlManager.getInstance(project)
  }

  fun `test modify iml`() {
    checkProjectAfterReload("common/modifyIml", "common/modifyIml") { (storage, projectDirUrl) ->
      val modules = storage.entities(ModuleEntity::class.java).sortedBy { it.name }.toList()
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

  fun `test add all libraries for directory based project`() {
    val checkAction = { (storage, projectDirUrl): ReloadedProjectData ->
      val libraries = storage.projectLibraries.sortedBy { it.name }.toList()
      assertEquals(1, libraries.size)
      val junitLibrary = libraries[0]
      assertEquals("junit2", junitLibrary.name)
      val root = assertOneElement(junitLibrary.roots.toList())
      assertEquals("jar://${JpsPathUtil.urlToPath(projectDirUrl)}/lib/junit2.jar!/", root.url.url)
    }
    val dirBasedData = reload(File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/imlUnderDotIdea"), "directoryBased/addLibrary")
    checkAction(dirBasedData)
  }

  fun `test add module`() {
    checkProjectAfterReload("directoryBased/addModule", "fileBased/addModule") { (storage, projectDirUrl) ->
      val modules = storage.entities(ModuleEntity::class.java).sortedBy { it.name }.toList()
      assertEquals(4, modules.size)
      val newModule = modules[1]
      assertEquals("newModule", newModule.name)
      val root = assertOneElement(newModule.sourceRoots.toList())
      assertEquals("$projectDirUrl/new", root.url.url)
    }
  }

  fun `test remove module`() {
    checkProjectAfterReload("directoryBased/removeModule", "fileBased/removeModule") { (storage, _) ->
      assertEquals(setOf("main", "xxx"), storage.entities(ModuleEntity::class.java).mapTo(HashSet()) {it.name})
    }
  }

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

  fun `test remove library`() {
    checkProjectAfterReload("directoryBased/removeLibrary", "fileBased/removeLibrary") { (storage, _) ->
      assertEquals(setOf("jarDir", "log4j"), storage.projectLibraries.mapTo(HashSet()) {it.name})
    }
  }

  fun `test remove all libraries`() {
    checkProjectAfterReload("directoryBased/removeAllLibraries", "fileBased/removeAllLibraries") { (storage, _) ->
      assertEquals(emptySet<String>(), storage.projectLibraries.mapTo(HashSet()) {it.name})
    }
  }

  private fun checkProjectAfterReload(directoryNameForDirectoryBased: String,
                                      directoryNameForFileBased: String,
                                      checkAction: (ReloadedProjectData) -> Unit) {
    val dirBasedData = reload(sampleDirBasedProjectFile, directoryNameForDirectoryBased)
    checkAction(dirBasedData)
    val fileBasedData = reload(sampleFileBasedProjectFile, directoryNameForFileBased)
    checkAction(fileBasedData)
  }

  private fun reload(originalProjectFile: File,
                     updateAction: (LoadedProjectData) -> JpsConfigurationFilesChange): ReloadedProjectData {
    val projectData = copyAndLoadProject(originalProjectFile, virtualFileManager)
    val change = updateAction(projectData)
    val (changedEntities, builder) = projectData.serializers.reloadFromChangedFiles(change, CachingJpsFileContentReader(projectData.projectDirUrl))
    val originalBuilder = WorkspaceEntityStorageBuilder.from(projectData.storage)
    originalBuilder.replaceBySource({it in changedEntities}, builder)
    projectData.serializers.checkConsistency(projectData.projectDirUrl, originalBuilder, virtualFileManager)
    return ReloadedProjectData(originalBuilder, projectData.projectDirUrl)
  }

  private fun reload(originalProjectDir: File, directoryName: String): ReloadedProjectData {
    return reload(originalProjectDir) { projectData ->
      val changedDir = PathManagerEx.findFileUnderCommunityHome("platform/workspaceModel/ide/tests/testData/serialization/reload/$directoryName")
      val newUrls = collectFileUrlsRec(changedDir, projectData.projectDirUrl, true) { it != "<delete/>"}
      val urlsToDelete = collectFileUrlsRec(changedDir, projectData.projectDirUrl, true) { it == "<delete/>"}
      val oldUrls = collectFileUrlsRec(projectData.projectDir, projectData.projectDirUrl, false) { true }
      FileUtil.copyDir(changedDir, projectData.projectDir)
      projectData.projectDir.walk().filter { it.isFile && it.readText().trim() == "<delete/>" }.forEach {
        FileUtil.delete(it)
      }

      JpsConfigurationFilesChange(addedFileUrls = newUrls - oldUrls, removedFileUrls = urlsToDelete,
                                  changedFileUrls = newUrls.intersect(oldUrls).toList())
    }
  }

  private fun collectFileUrlsRec(dir: File,
                                 baseUrl: String,
                                 replaceByParent: Boolean,
                                 contentFilter: (String) -> Boolean): Set<String> {
    val res = HashSet<String>()

    dir.walkBottomUp().forEach { file ->
      if (file.isFile && contentFilter(file.readText().trim())) {
        res += toRelativeUrl(baseUrl, dir, file)
      }
      else if (file.isDirectory) {
        // If all removed files relate to the one directory, the directory event is sent
        val relativeFileUrl = FileUtil.getRelativePath(dir, file) ?: return@forEach
        if ("." == relativeFileUrl) return@forEach
        if (!relativeFileUrl.endsWith(".idea/libraries") && !relativeFileUrl.endsWith(".idea/artifacts")) return@forEach

        // Existing files + files that will be copied
        val children = (JpsPathUtil.urlToFile(toRelativeUrl(baseUrl, dir, file)).listFiles()?.map { JpsPathUtil.pathToUrl(it.absolutePath) } ?: emptyList()) +
                       (file.listFiles()?.map { toRelativeUrl(baseUrl, dir, it) } ?: emptyList())
        if (children.all { it in res }) {
          if (replaceByParent) children.forEach { res.remove(it) }
          res += toRelativeUrl(baseUrl, dir, file)
        }
      }
    }

    return res
  }

  private fun toRelativeUrl(baseUrl: String, dir: File, file: File): String {
    return "$baseUrl/${FileUtil.toSystemIndependentName(FileUtil.getRelativePath(dir, file)!!)}"
  }

  private data class ReloadedProjectData(val storage: WorkspaceEntityStorageBuilder, val projectDirUrl: String)
}
