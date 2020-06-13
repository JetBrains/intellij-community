package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.workspaceModel.ide.impl.jps.serialization.asConfigLocation
import com.intellij.workspaceModel.ide.impl.jps.serialization.loadProject
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.*
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
    val builder = WorkspaceEntityStorageBuilder.create()
    val entity = builder.addEntity(ModifiableSampleEntity::class.java, Source) {
      this.data = "Test"
    }
    val index = builder.getMutableExternalMapping<String>("MyIndex")
    index.addMapping(entity, "Hello")

    serializationRoundTrip(builder)
  }

  private fun loadProjectAndCheck(projectFile: File) {
    val storageBuilder = WorkspaceEntityStorageBuilder.create()
    loadProject(projectFile.asConfigLocation(virtualFileManager), storageBuilder, virtualFileManager)
    serializationRoundTrip(storageBuilder)
  }

  private fun serializationRoundTrip(storageBuilder: WorkspaceEntityStorageBuilder) {
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
