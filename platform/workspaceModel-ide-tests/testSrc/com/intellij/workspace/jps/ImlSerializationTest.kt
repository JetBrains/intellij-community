package com.intellij.workspace.jps

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.workspace.api.*
import com.intellij.workspace.api.pstorage.EntityDataDelegation
import com.intellij.workspace.api.pstorage.PEntityData
import com.intellij.workspace.api.pstorage.PModifiableTypedEntity
import com.intellij.workspace.api.pstorage.PTypedEntity
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
  fun communityProject() {
    val projectDir = File(PathManagerEx.getCommunityHomePath())
    loadProjectAndCheck(projectDir)
  }

  @Test
  fun externalIndexIsNotSerialized() {
    val builder = TypedEntityStorageBuilder.create()
    val entity = builder.addEntity(ModifiableSampleEntity::class.java, Source) {
      this.data = "Test"
    }
    val index = builder.getOrCreateExternalIndex<String>("MyIndex")
    index.index(entity, "Hello")

    serializationRoundTrip(builder)
  }

  private fun loadProjectAndCheck(projectFile: File) {
    val storageBuilder = TypedEntityStorageBuilder.create()
    loadProject(projectFile.asConfigLocation(virtualFileManager), storageBuilder, virtualFileManager)
    serializationRoundTrip(storageBuilder)
  }

  private fun serializationRoundTrip(storageBuilder: TypedEntityStorageBuilder) {
    val storage = storageBuilder.toStorage()
    val timeMillis = measureTimeMillis {
      val byteArray = SerializationRoundTripChecker.verifyPSerializationRoundTrip(storage, virtualFileManager)
      println("Serialized size: ${byteArray.size}")
    }
    println("Time: $timeMillis ms")
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}

@Suppress("unused")
internal class SampleEntityData : PEntityData<SampleEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: TypedEntityStorage): SampleEntity {
    return SampleEntity(data).also { addMetaData(it, snapshot) }
  }
}

internal class SampleEntity(val data: String) : PTypedEntity()

internal class ModifiableSampleEntity : PModifiableTypedEntity<SampleEntity>() {
  var data: String by EntityDataDelegation()
}

internal object Source : EntitySource
