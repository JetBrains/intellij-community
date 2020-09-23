package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.workspaceModel.ide.impl.jps.serialization.asConfigLocation
import com.intellij.workspaceModel.ide.impl.jps.serialization.loadProject
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.*
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

class ImlSerializationTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun sampleProject() {
    val projectDir = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    loadProjectAndCheck(projectDir)
  }

  @Test
  fun sizeCheck() {
    val projectDir = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    val bytes = loadProjectAndCheck(projectDir)

    checkSerializationSize(bytes, 42_620, 2_000)
  }

  @Test
  fun communityProject() {
    val projectDir = File(PathManagerEx.getCommunityHomePath())
    loadProjectAndCheck(projectDir)
  }

  @Test
  fun externalIndexIsNotSerialized() {
    val builder = WorkspaceEntityStorageBuilder.create()
    val entity = builder.addEntity(ModifiableSampleEntity::class.java, Source) {
      this.data = "Test"
    }
    val index = builder.getMutableExternalMapping<String>("MyIndex")
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
    val storageBuilder = WorkspaceEntityStorageBuilder.create()
    loadProject(projectFile.asConfigLocation(virtualFileManager), storageBuilder, virtualFileManager)
    return serializationRoundTrip(storageBuilder)
  }

  private fun serializationRoundTrip(storageBuilder: WorkspaceEntityStorageBuilder): ByteArray {
    val storage = storageBuilder.toStorage()
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

@Suppress("unused")
internal class SampleEntityData : WorkspaceEntityData<SampleEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): SampleEntity {
    return SampleEntity(data).also { addMetaData(it, snapshot) }
  }
}

internal class SampleEntity(val data: String) : WorkspaceEntityBase()

internal class ModifiableSampleEntity : ModifiableWorkspaceEntityBase<SampleEntity>() {
  var data: String by EntityDataDelegation()
}

internal object Source : EntitySource
