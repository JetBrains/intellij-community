package com.intellij.workspace.jps

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.workspace.api.KryoEntityStorageSerializer
import com.intellij.workspace.api.TestEntityTypesResolver
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.verifySerializationRoundTrip
import org.junit.ClassRule
import org.junit.Test
import java.io.File

class ImlSerializationTest {
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
    JpsProjectEntitiesLoader.loadProject(projectFile.asStoragePlace(), storageBuilder)
    val storage = storageBuilder.toStorage()
    val byteArray = verifySerializationRoundTrip(storage, KryoEntityStorageSerializer(TestEntityTypesResolver()))
    println("Serialized size: ${byteArray.size}")
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}
