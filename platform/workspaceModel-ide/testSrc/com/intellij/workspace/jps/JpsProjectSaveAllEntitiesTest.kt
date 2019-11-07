package com.intellij.workspace.jps

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
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

  private fun checkLoadSave(originalProjectFile: File) {
    val projectData = copyAndLoadProject(originalProjectFile)
    FileUtil.delete(projectData.projectDir)
    val writer = JpsFileContentWriterImpl()
    projectData.serializationData.saveAllEntities(projectData.storage, writer)
    writer.writeFiles(projectData.projectDir)
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
