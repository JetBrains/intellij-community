package com.intellij.workspace.jps

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.VirtualFileUrlManager
import com.intellij.workspace.api.verifyPSerializationRoundTrip
import com.intellij.workspace.ide.VirtualFileUrlManagerImpl
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.io.File

class ImlSerializationTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun sampleProject() {
    val projectDir = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    serializationRoundTrip(projectDir)
  }

  @Test
  fun communityProject() {
    val projectDir = File(PathManagerEx.getCommunityHomePath())
    serializationRoundTrip(projectDir)
  }

  private fun serializationRoundTrip(projectFile: File) {
    val storageBuilder = TypedEntityStorageBuilder.create()
    loadProject(projectFile.asConfigLocation(virtualFileManager), storageBuilder, virtualFileManager)
    val storage = storageBuilder.toStorage()
    val byteArray = verifyPSerializationRoundTrip(storage, virtualFileManager)
    println("Serialized size: ${byteArray.size}")
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}
