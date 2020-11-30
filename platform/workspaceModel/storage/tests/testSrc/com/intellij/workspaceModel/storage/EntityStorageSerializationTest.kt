// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.bridgeEntities.addLibraryEntity
import com.intellij.workspaceModel.storage.entities.MySource
import com.intellij.workspaceModel.storage.entities.addSampleEntity
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
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

    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())
    val deserializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())
      .also { it.serializerDataFormatVersion = "XYZ" }

    val stream = ByteArrayOutputStream()
    serializer.serializeCache(stream, builder.toStorage())

    val byteArray = stream.toByteArray()
    val deserialized = (deserializer.deserializeCache(ByteArrayInputStream(byteArray)) as? WorkspaceEntityStorageBuilderImpl)?.toStorage()

    assertNull(deserialized)
  }

  @Test
  fun `serializer version`() {
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())

    val kryo = serializer.createKryo()

    val registration = (10..1_000).mapNotNull { kryo.getRegistration(it) }.joinToString(separator = "\n")

    assertEquals("Have you changed kryo registration? Update the version number! (And this test)", expectedKryoRegistration, registration)
  }

  @Test
  fun `serialize empty lists`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), virtualFileManager)

    val builder = WorkspaceEntityStorageBuilder.create() as WorkspaceEntityStorageBuilderImpl

    // Do not replace ArrayList() with emptyList(). This must be a new object for this test
    builder.addLibraryEntity("myName", LibraryTableId.ProjectLibraryTableId, ArrayList(), ArrayList(), MySource)

    val stream = ByteArrayOutputStream()
    serializer.serializeCache(stream, builder.toStorage())
  }
}

// Kotlin tip: Use the ugly ${'$'} to insert the $ into the multiline string
private val expectedKryoRegistration = """
  [10, com.intellij.workspaceModel.storage.impl.EntityId]
  [11, com.google.common.collect.HashMultimap]
  [12, com.intellij.workspaceModel.storage.impl.ConnectionId]
  [13, com.intellij.workspaceModel.storage.impl.ImmutableEntitiesBarrel]
  [14, com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl${'$'}TypeInfo]
  [15, java.util.ArrayList]
  [16, java.util.HashMap]
  [17, com.intellij.util.SmartList]
  [18, java.util.LinkedHashMap]
  [19, com.intellij.util.containers.BidirectionalMap]
  [20, com.intellij.workspaceModel.storage.impl.containers.BidirectionalSetMap]
  [21, java.util.HashSet]
  [22, com.intellij.util.containers.BidirectionalMultiMap]
  [23, com.google.common.collect.HashBiMap]
  [24, com.intellij.workspaceModel.storage.impl.containers.LinkedBidirectionalMap]
  [25, it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap]
  [26, java.util.Arrays${'$'}ArrayList]
  [27, byte[]]
  [28, com.intellij.workspaceModel.storage.impl.ImmutableEntityFamily]
  [29, com.intellij.workspaceModel.storage.impl.RefsTable]
  [30, com.intellij.workspaceModel.storage.impl.containers.ImmutableNonNegativeIntIntBiMap]
  [31, com.intellij.workspaceModel.storage.impl.containers.ImmutableIntIntUniqueBiMap]
  [32, com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex]
  [33, com.intellij.workspaceModel.storage.impl.indices.EntityStorageInternalIndex]
  [34, com.intellij.workspaceModel.storage.impl.containers.ImmutableNonNegativeIntIntMultiMap${'$'}ByList]
  [35, int[]]
  [36, kotlin.Pair]
  [37, com.intellij.workspaceModel.storage.impl.indices.MultimapStorageIndex]
  [38, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}AddEntity]
  [39, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}RemoveEntity]
  [40, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ReplaceEntity]
  [41, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ChangeEntitySource]
  [42, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ReplaceAndChangeSource]
  [43, java.util.LinkedHashSet]
  [44, java.util.Collections${'$'}UnmodifiableCollection]
  [45, java.util.Collections${'$'}UnmodifiableSet]
  [46, java.util.Collections${'$'}UnmodifiableRandomAccessList]
  [47, java.util.Collections${'$'}UnmodifiableMap]
  [48, java.util.Collections${'$'}EmptyList]
  [49, java.util.Collections${'$'}EmptyMap]
  [50, java.util.Collections${'$'}EmptySet]
  [51, java.util.Collections${'$'}SingletonList]
  [52, java.util.Collections${'$'}SingletonMap]
  [53, java.util.Collections${'$'}SingletonSet]
  [54, com.intellij.util.containers.ContainerUtilRt${'$'}EmptyList]
  [55, com.intellij.util.containers.MostlySingularMultiMap${'$'}EmptyMap]
  [56, com.intellij.util.containers.MultiMap${'$'}EmptyMap]
  [57, kotlin.collections.EmptyMap]
  [58, kotlin.collections.EmptyList]
  [59, kotlin.collections.EmptySet]
""".trimIndent()
