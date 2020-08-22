// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.addSampleEntity
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.impl.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import junit.framework.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class EntityStorageSerializationTest {
  @Test
  fun `simple model serialization`() {
    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addSampleEntity("MyEntity")

    SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toStorage(), VirtualFileUrlManagerImpl())
  }

  @Test
  fun `serialization with version changing`() {
    val builder = WorkspaceEntityStorageBuilder.create() as WorkspaceEntityStorageBuilderImpl
    builder.addSampleEntity("MyEntity")

    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())
    val deserializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())
      .also { it.serializerDataFormatVersion = "v2" }

    val stream = ByteArrayOutputStream()
    serializer.serializeCache(stream, builder.toStorage())

    val byteArray = stream.toByteArray()
    val deserialized = (deserializer.deserializeCache(ByteArrayInputStream(byteArray)) as? WorkspaceEntityStorageBuilderImpl)?.toStorage()

    assertNull(deserialized)
  }
}
