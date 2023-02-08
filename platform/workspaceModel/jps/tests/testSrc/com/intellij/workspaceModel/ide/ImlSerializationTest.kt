package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.jps.serialization.asConfigLocation
import com.intellij.workspaceModel.ide.impl.jps.serialization.loadProject
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.SerializationRoundTripChecker
import com.intellij.workspaceModel.storage.entities.test.api.SampleEntity2
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

class ImlSerializationTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManager.getInstance(projectModel.project)
  }

  @Test
  fun sampleProject() {
    val projectDir = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    loadProjectAndCheck(projectDir)
  }

  @Test
  fun sizeCheck() {
    val expectedSize = 19_000
    val projectDir = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    val bytes = loadProjectAndCheck(projectDir)

    checkSerializationSize(bytes, expectedSize, 2_000)

    assertTrue("This assertion is a reminder. Have you updated the serializer? Update the serializer version!",
               "v43" == EntityStorageSerializerImpl.SERIALIZER_VERSION)
  }

  @Test
  fun communityProject() {
    val projectDir = File(PathManagerEx.getCommunityHomePath())
    loadProjectAndCheck(projectDir)
  }

  @Test
  fun ultimateProject() {
    val projectDir = File("/Users/Alex.Plate/Develop/Work/intellij")
    loadProjectAndCheck(projectDir)
  }

  @Test
  fun externalIndexIsNotSerialized() {
    val builder = MutableEntityStorage.create()
    val entity = SampleEntity2("Test", true, Source)
    builder.addEntity(entity)
    val index = builder.getMutableExternalMapping<String>("test.my.index")
    index.addMapping(entity, "Hello")

    serializationRoundTrip(builder)
  }

  private fun checkSerializationSize(bytes: ByteArray, expectedSize: Int, precision:Int) {

    // At the moment serialization size varies from time to time. I don't know the reason for that, but you should check this test if
    //   the serialization size changes a lot.
    // Maybe you've added a new field to the entity store structure. Recheck if you really want this field to be included.
    val leftBound = expectedSize - precision
    val rightBound = expectedSize + precision
    assertTrue("Expected size: $expectedSize, precision: $precision, real size: ${bytes.size}", bytes.size in leftBound..rightBound)
  }

  private fun loadProjectAndCheck(projectFile: File): ByteArray {
    val storageBuilder = MutableEntityStorage.create()
    loadProject(projectFile.asConfigLocation(virtualFileManager), storageBuilder, virtualFileManager)
    return serializationRoundTrip(storageBuilder)
  }

  private fun serializationRoundTrip(storageBuilder: MutableEntityStorage): ByteArray {
    val storage = storageBuilder.toSnapshot()
    val byteArray: ByteArray
    val timeMillis = measureTimeMillis {
      byteArray = SerializationRoundTripChecker.verifyPSerializationRoundTrip(storage, virtualFileManager)
      println("Serialized size: ${byteArray.size}")
    }
    println("Time: $timeMillis ms")
    return byteArray
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}

internal object Source : EntitySource
