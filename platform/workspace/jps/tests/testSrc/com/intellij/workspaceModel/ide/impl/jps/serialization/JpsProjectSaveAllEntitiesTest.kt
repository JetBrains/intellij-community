package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.JpsEntitySourceFactory
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class JpsProjectSaveAllEntitiesTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun setUp() {
    virtualFileManager = WorkspaceModel.getInstance(projectModel.project).getVirtualFileUrlManager()
  }

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
    val projectFile = File(PathManagerEx.getCommunityHomePath(),
                           "platform/workspace/jps/tests/testData/serialization/facets/facets.ipr")
    checkLoadSave(projectFile)
  }

  @Test
  fun `test order of attributes in NewModuleRootManager component`() {
    val projectFile = File(PathManagerEx.getCommunityHomePath(),
                           "platform/workspace/jps/tests/testData/serialization/orderOfAttributesInRootManagerTag/orderOfAttributes.ipr")
    checkLoadSave(projectFile)
  }

  @Test
  fun `add library to empty project`() {
    val projectDir = FileUtil.createTempDirectory("jpsSaveTest", null)
    val (serializers, configLocation) = createProjectSerializers(projectDir, virtualFileManager)
    val builder = MutableEntityStorage.create()
    val jarUrl = virtualFileManager.getOrCreateFromUri("jar://${projectDir.systemIndependentPath}/lib/foo.jar!/")
    val libraryRoot = LibraryRoot(jarUrl, LibraryRootTypeId.COMPILED)
    val source = JpsEntitySourceFactory.createJpsEntitySourceForProjectLibrary(projectDir.asConfigLocation(virtualFileManager))
    builder addEntity LibraryEntity("foo", LibraryTableId.ProjectLibraryTableId, listOf(libraryRoot), source)
    val storage = builder.toSnapshot()
    serializers.saveAllEntities(storage, configLocation)
    val expectedDir = File(PathManagerEx.getCommunityHomePath(),
                           "platform/workspace/jps/tests/testData/serialization/fromScratch/addLibrary")
    assertDirectoryMatches(projectDir, expectedDir, emptySet(), emptyList())
  }

  @Test
  fun `escape special symbols in library name`() {
    val projectDir = FileUtil.createTempDirectory("jpsSaveTest", null)
    val (serializers, configLocation) = createProjectSerializers(projectDir, virtualFileManager)
    val builder = MutableEntityStorage.create()
    for (libName in listOf("a lib", "my-lib", "group-id:artifact-id")) {
      val source = JpsEntitySourceFactory.createJpsEntitySourceForProjectLibrary(projectDir.asConfigLocation(virtualFileManager))
      builder addEntity LibraryEntity(libName, LibraryTableId.ProjectLibraryTableId, emptyList(), source)
    }
    val storage = builder.toSnapshot()
    serializers.saveAllEntities(storage, configLocation)
    val expectedDir = File(PathManagerEx.getCommunityHomePath(),
                           "platform/workspace/jps/tests/testData/serialization/specialSymbolsInLibraryName")
    assertDirectoryMatches(projectDir, expectedDir, emptySet(), emptyList())
  }

  @Test
  fun `escape special symbols in library name2`() {
    val projectDir = FileUtil.createTempDirectory("jpsSaveTest", null)
    val (serializers, configLocation) = createProjectSerializers(projectDir, virtualFileManager)


    val builder = MutableEntityStorage.create()
    for (libName in listOf("a lib", "my-lib", "group-id:artifact-id")) {
      val source = JpsEntitySourceFactory.createJpsEntitySourceForProjectLibrary(projectDir.asConfigLocation(virtualFileManager))
      builder addEntity LibraryEntity(libName, LibraryTableId.ProjectLibraryTableId, emptyList(), source)
    }
    val storage = builder.toSnapshot()
    serializers.saveAllEntities(storage, configLocation)
    val expectedDir = File(PathManagerEx.getCommunityHomePath(),
                           "platform/workspace/jps/tests/testData/serialization/specialSymbolsInLibraryName")
    assertDirectoryMatches(projectDir, expectedDir, emptySet(), emptyList())
  }

  private fun checkLoadSave(originalProjectFile: File) {
    val projectData = copyAndLoadProject(originalProjectFile, virtualFileManager)
    FileUtil.delete(projectData.projectDir)
    projectData.serializers.saveAllEntities(projectData.storage, projectData.configLocation)
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
