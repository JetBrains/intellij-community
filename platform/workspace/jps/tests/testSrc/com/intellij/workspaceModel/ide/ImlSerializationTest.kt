// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.ExternalMappingKey
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity2
import com.intellij.platform.workspace.storage.tests.SerializationRoundTripChecker
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.jps.serialization.asConfigLocation
import com.intellij.workspaceModel.ide.impl.jps.serialization.loadProject
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

class ImlSerializationTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager

  private val externalMappingKey = ExternalMappingKey.create<Any>("test.my.index")

  @Before
  fun setUp() {
    virtualFileManager = WorkspaceModel.getInstance(projectModel.project).getVirtualFileUrlManager()
  }

  @Test
  fun sampleProject() {
    val projectDir = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    loadProjectAndCheck(projectDir)
  }

  @Test
  fun sizeCheck() {
    val expectedSize = 17_500
    val projectDir = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    val bytes = loadProjectAndCheck(projectDir)

    checkSerializationSize(bytes, expectedSize, 3_500)

    @Suppress("KotlinConstantConditions")
    assertTrue("version13" == EntityStorageSerializerImpl.STORAGE_SERIALIZATION_VERSION,
               "This assertion is a reminder. Have you updated the serializer? Update the serialization version!")
  }

  @Test
  fun communityProject() {
    val projectDir = File(PathManagerEx.getCommunityHomePath())
    loadProjectAndCheck(projectDir)
  }

  @Test
  fun externalIndexIsNotSerialized() {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity SampleEntity2("Test", true, Source)
    val index = builder.getMutableExternalMapping(externalMappingKey)
    index.addMapping(entity, "Hello")

    serializationRoundTrip(builder)
  }

  private fun checkSerializationSize(bytes: ByteArray, expectedSize: Int, precision: Int) {

    // At the moment serialization size varies from time to time. I don't know the reason for that, but you should check this test if
    //   the serialization size changes a lot.
    // Maybe you've added a new field to the entity store structure. Recheck if you really want this field to be included.
    val leftBound = expectedSize - precision
    val rightBound = expectedSize + precision
    assertTrue(bytes.size in leftBound..rightBound, "Expected size: $expectedSize, precision: $precision, real size: ${bytes.size}")
  }

  private fun loadProjectAndCheck(projectFile: File): ByteArray {
    val storageBuilder = MutableEntityStorage.create()
    loadProject(projectFile.asConfigLocation(virtualFileManager), storageBuilder, storageBuilder, virtualFileManager)
    return serializationRoundTrip(storageBuilder)
  }

  private fun serializationRoundTrip(storageBuilder: MutableEntityStorage): ByteArray {
    val storage = storageBuilder.toSnapshot()
    val byteArray: ByteArray
    val timeMillis = measureTimeMillis {
      val res = SerializationRoundTripChecker.verifyPSerializationRoundTrip(storage, virtualFileManager)
      byteArray = res.first
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
