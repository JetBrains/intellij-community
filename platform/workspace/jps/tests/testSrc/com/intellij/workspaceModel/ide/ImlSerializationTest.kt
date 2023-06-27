// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity2
import com.intellij.platform.workspace.storage.tests.SerializationRoundTripChecker
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.jps.serialization.asConfigLocation
import com.intellij.workspaceModel.ide.impl.jps.serialization.loadProject
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
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
    val expectedSize = 17_000
    val projectDir = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    val bytes = loadProjectAndCheck(projectDir)

    checkSerializationSize(bytes, expectedSize, 2_000)

    @Suppress("KotlinConstantConditions")
    assertTrue("v50" == EntityStorageSerializerImpl.SERIALIZER_VERSION,
               "This assertion is a reminder. Have you updated the serializer? Update the serializer version!")
  }

  @Test
  fun communityProject() {
    val projectDir = File(PathManagerEx.getCommunityHomePath())
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
