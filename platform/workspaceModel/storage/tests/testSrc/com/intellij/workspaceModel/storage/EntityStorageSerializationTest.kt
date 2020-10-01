// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.addSampleEntity
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import junit.framework.Assert.assertEquals
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

    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl(), true)
    val deserializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl(), true)
      .also { it.serializerDataFormatVersion = "XYZ" }

    val stream = ByteArrayOutputStream()
    serializer.serializeCache(stream, builder.toStorage())

    val byteArray = stream.toByteArray()
    val deserialized = (deserializer.deserializeCache(ByteArrayInputStream(byteArray)) as? WorkspaceEntityStorageBuilderImpl)?.toStorage()

    assertNull(deserialized)
  }

  @Test
  fun `serializer version`() {
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl(), true)

    val kryo = serializer.createKryo()

    val registration = (10..1_000).mapNotNull { kryo.getRegistration(it) }.joinToString(separator = "\n")

    assertEquals("Have you changed kryo registration? Update the version number! (And this test)", expectedKryoRegistration, registration)
  }
}

// Kotlin tip: Use the ugly ${'$'} to insert the $ into the multiline string
private val expectedKryoRegistration = """
  [10, com.intellij.workspaceModel.storage.VirtualFileUrl]
  [11, com.intellij.workspaceModel.storage.impl.EntityId]
  [12, com.google.common.collect.HashMultimap]
  [13, com.intellij.workspaceModel.storage.impl.ConnectionId]
  [14, com.intellij.workspaceModel.storage.impl.ImmutableEntitiesBarrel]
  [15, com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl${'$'}TypeInfo]
  [16, java.util.ArrayList]
  [17, java.util.HashMap]
  [18, com.intellij.util.SmartList]
  [19, java.util.LinkedHashMap]
  [20, com.intellij.util.containers.BidirectionalMap]
  [21, com.intellij.workspaceModel.storage.impl.containers.BidirectionalSetMap]
  [22, java.util.HashSet]
  [23, com.intellij.util.containers.BidirectionalMultiMap]
  [24, com.google.common.collect.HashBiMap]
  [25, com.intellij.workspaceModel.storage.impl.containers.LinkedBidirectionalMap]
  [26, it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap]
  [27, java.util.Arrays${'$'}ArrayList]
  [28, byte[]]
  [29, com.intellij.workspaceModel.storage.impl.ImmutableEntityFamily]
  [30, com.intellij.workspaceModel.storage.impl.RefsTable]
  [31, com.intellij.workspaceModel.storage.impl.containers.ImmutableNonNegativeIntIntBiMap]
  [32, com.intellij.workspaceModel.storage.impl.containers.ImmutableIntIntUniqueBiMap]
  [33, com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex]
  [34, com.intellij.workspaceModel.storage.impl.indices.EntityStorageInternalIndex]
  [35, com.intellij.workspaceModel.storage.impl.containers.ImmutableNonNegativeIntIntMultiMap${'$'}ByList]
  [36, int[]]
  [37, kotlin.Pair]
  [38, com.intellij.workspaceModel.storage.impl.indices.MultimapStorageIndex]
  [39, com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex${'$'}VirtualFileUrlInfo]
  [40, com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl${'$'}ChangeEntry${'$'}AddEntity]
  [41, com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl${'$'}ChangeEntry${'$'}RemoveEntity]
  [42, com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl${'$'}ChangeEntry${'$'}ReplaceEntity]
  [43, com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl${'$'}ChangeEntry${'$'}ChangeEntitySource]
  [44, java.util.LinkedHashSet]
  [45, java.util.Collections${'$'}UnmodifiableCollection]
  [46, java.util.Collections${'$'}UnmodifiableSet]
  [47, java.util.Collections${'$'}UnmodifiableRandomAccessList]
  [48, java.util.Collections${'$'}UnmodifiableMap]
  [49, java.util.Collections${'$'}EmptyList]
  [50, java.util.Collections${'$'}EmptyMap]
  [51, java.util.Collections${'$'}EmptySet]
  [52, java.util.Collections${'$'}SingletonList]
  [53, java.util.Collections${'$'}SingletonMap]
  [54, java.util.Collections${'$'}SingletonSet]
  [55, com.intellij.util.containers.ContainerUtilRt${'$'}EmptyList]
  [56, com.intellij.util.containers.MostlySingularMultiMap${'$'}EmptyMap]
  [57, com.intellij.util.containers.MultiMap${'$'}EmptyMap]
  [58, kotlin.collections.EmptyMap]
  [59, kotlin.collections.EmptyList]
  [60, kotlin.collections.EmptySet]
""".trimIndent()
