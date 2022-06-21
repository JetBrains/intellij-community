package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.JpsEntitySourceFactory
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryRoot
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryRootTypeId
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.bridgeEntities.addLibraryEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class JpsProjectSaveAllEntitiesTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule(true)

  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManager.getInstance(projectModel.project)
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
                           "platform/workspaceModel/jps/tests/testData/serialization/facets/facets.ipr")
    checkLoadSave(projectFile)
  }

  @Test
  fun `test order of attributes in NewModuleRootManager component`() {
    val projectFile = File(PathManagerEx.getCommunityHomePath(),
                           "platform/workspaceModel/jps/tests/testData/serialization/orderOfAttributesInRootManagerTag/orderOfAttributes.ipr")
    checkLoadSave(projectFile)
  }

  @Test
  fun `add library to empty project`() {
    val projectDir = FileUtil.createTempDirectory("jpsSaveTest", null)
    val (serializers, configLocation) = createProjectSerializers(projectDir, virtualFileManager)
    val builder = WorkspaceEntityStorageBuilder.create()
    val jarUrl = virtualFileManager.fromUrl("jar://${projectDir.systemIndependentPath}/lib/foo.jar!/")
    val libraryRoot = LibraryRoot(jarUrl, LibraryRootTypeId.COMPILED)
    val source = JpsEntitySourceFactory.createJpsEntitySourceForProjectLibrary(projectDir.asConfigLocation(virtualFileManager))
    builder.addLibraryEntity("foo", LibraryTableId.ProjectLibraryTableId, listOf(libraryRoot), emptyList(), source)
    val storage = builder.toStorage()
    serializers.saveAllEntities(storage, configLocation)
    val expectedDir = File(PathManagerEx.getCommunityHomePath(),
                           "platform/workspaceModel/jps/tests/testData/serialization/fromScratch/addLibrary")
    assertDirectoryMatches(projectDir, expectedDir, emptySet(), emptyList())
  }

  @Test
  fun `escape special symbols in library name`() {
    val projectDir = FileUtil.createTempDirectory("jpsSaveTest", null)
    val (serializers, configLocation) = createProjectSerializers(projectDir, virtualFileManager)
    val builder = WorkspaceEntityStorageBuilder.create()
    for (libName in listOf("a lib", "my-lib", "group-id:artifact-id")) {
      val source = JpsEntitySourceFactory.createJpsEntitySourceForProjectLibrary(projectDir.asConfigLocation(virtualFileManager))
      builder.addLibraryEntity(libName, LibraryTableId.ProjectLibraryTableId, emptyList(), emptyList(), source)
    }
    val storage = builder.toStorage()
    serializers.saveAllEntities(storage, configLocation)
    val expectedDir = File(PathManagerEx.getCommunityHomePath(),
                           "platform/workspaceModel/jps/tests/testData/serialization/specialSymbolsInLibraryName")
    assertDirectoryMatches(projectDir, expectedDir, emptySet(), emptyList())
  }

  @Test
  fun `escape special symbols in library name2`() {
    val projectDir = FileUtil.createTempDirectory("jpsSaveTest", null)
    val (serializers, configLocation) = createProjectSerializers(projectDir, virtualFileManager)


    val builder = WorkspaceEntityStorageBuilder.create()
    for (libName in listOf("a lib", "my-lib", "group-id:artifact-id")) {
      val source = JpsEntitySourceFactory.createJpsEntitySourceForProjectLibrary(projectDir.asConfigLocation(virtualFileManager))
      builder.addLibraryEntity(libName, LibraryTableId.ProjectLibraryTableId, emptyList(), emptyList(), source)
    }
    val storage = builder.toStorage()
    serializers.saveAllEntities(storage, configLocation)
    val expectedDir = File(PathManagerEx.getCommunityHomePath(),
                           "platform/workspaceModel/jps/tests/testData/serialization/specialSymbolsInLibraryName")
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
