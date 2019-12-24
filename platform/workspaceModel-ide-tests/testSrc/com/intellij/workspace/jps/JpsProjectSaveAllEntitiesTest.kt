package com.intellij.workspace.jps

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.testFramework.ApplicationRule
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.IdeUiEntitySource
import org.junit.ClassRule
import org.junit.Test
import java.io.File

class JpsProjectSaveAllEntitiesTest {
  @Test
  fun `test save sample directory based project`() {
    val projectFile = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    checkLoadSave(projectFile)
  }

  @Test
  fun `test save sample ipr project`() {
    val projectFile = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject-ipr/sampleProject.ipr")
    checkLoadSave(projectFile)
  }

  @Test
  fun `test save facets`() {
    val projectFile = File(PathManagerEx.getCommunityHomePath(), "platform/workspaceModel-ide-tests/testData/serialization/facets/facets.ipr")
    checkLoadSave(projectFile)
  }

  @Test
  fun `add library to empty project`() {
    val projectDir = FileUtil.createTempDirectory("jpsSaveTest", null)
    val serializers = createSerializationData(projectDir)
    val builder = TypedEntityStorageBuilder.create()
    val jarUrl = VirtualFileUrlManager.fromUrl("jar://${projectDir.systemIndependentPath}/lib/foo.jar!/")
    val libraryRoot = LibraryRoot(jarUrl, LibraryRootTypeId("CLASSES"), LibraryRoot.InclusionOptions.ROOT_ITSELF)
    builder.addLibraryEntity("foo", LibraryTableId.ProjectLibraryTableId, listOf(libraryRoot), emptyList(), IdeUiEntitySource)
    val storage = builder.toStorage()
    serializers.saveAllEntities(storage, projectDir)
    val expectedDir = File(PathManagerEx.getCommunityHomePath(),
                           "platform/workspaceModel-ide-tests/testData/serialization/fromScratch/addLibrary")
    assertDirectoryMatches(projectDir, expectedDir, emptySet(), emptyList())
  }

  @Test
  fun `escape special symbols in library name`() {
    val projectDir = FileUtil.createTempDirectory("jpsSaveTest", null)
    val serializers = createSerializationData(projectDir)
    val builder = TypedEntityStorageBuilder.create()
    for (libName in listOf("a lib", "my-lib", "group-id:artifact-id")) {
      builder.addLibraryEntity(libName, LibraryTableId.ProjectLibraryTableId, emptyList(), emptyList(), IdeUiEntitySource)
    }
    val storage = builder.toStorage()
    serializers.saveAllEntities(storage, projectDir)
    val expectedDir = File(PathManagerEx.getCommunityHomePath(),
                           "platform/workspaceModel-ide-tests/testData/serialization/specialSymbolsInLibraryName")
    assertDirectoryMatches(projectDir, expectedDir, emptySet(), emptyList())
  }

  private fun checkLoadSave(originalProjectFile: File) {
    val projectData = copyAndLoadProject(originalProjectFile)
    FileUtil.delete(projectData.projectDir)
    projectData.serializationData.saveAllEntities(projectData.storage, projectData.projectDir)
    assertDirectoryMatches(projectData.projectDir, projectData.originalProjectDir,
                           setOf(".idea/misc.xml", ".idea/encodings.xml", ".idea/compiler.xml", ".idea/.name"),
                           listOf("CompilerConfiguration", "Encoding", "ProjectRootManager"))
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}
