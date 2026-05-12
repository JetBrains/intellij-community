// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.hprof

import com.intellij.diagnostic.hprof.parser.ConstantPoolEntry
import com.intellij.diagnostic.hprof.parser.InstanceFieldEntry
import com.intellij.diagnostic.hprof.parser.StaticFieldEntry
import com.intellij.diagnostic.hprof.parser.Type
import com.intellij.diagnostic.hprof.util.HprofWriter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path

class PersistentSyntaxTreeHprofProcessorTest {
  @Test
  fun `extracts versioned payload maps`(@TempDir tempDir: Path) {
    val hprofPath = tempDir.resolve("versioned-payload-maps.hprof")
    writeExtractionHprof(hprofPath)

    val extraction = PersistentSyntaxTreeHprofProcessor.extractVersionedPayloadMaps(hprofPath)

    assertEquals(3, extraction.allInstances.size)

    val map1 = extraction.map1Instances.single()
    assertEquals(MAP1_OBJECT_ID, map1.objectId)
    assertEquals(VersionedPayloadMapLayout.MAP1, map1.layout)
    assertEquals(PersistentSyntaxTreeHprofProcessor.VERSIONED_PAYLOAD_MAP1_CLASS_NAME, map1.className)
    assertEquals(listOf(7L), map1.entries.map { it.version })
    assertEquals(PAYLOAD_B_ID, map1.entries.single().payloadObjectId)
    assertEquals(PAYLOAD_CLASS_NAME, map1.entries.single().payloadClassName)

    val map2 = extraction.map2Instances.single()
    assertEquals(MAP2_OBJECT_ID, map2.objectId)
    assertEquals(VersionedPayloadMapLayout.MAP2, map2.layout)
    assertEquals(PersistentSyntaxTreeHprofProcessor.VERSIONED_PAYLOAD_MAP2_CLASS_NAME, map2.className)
    assertEquals(listOf(10L, 20L), map2.entries.map { it.version })
    assertEquals(PAYLOAD_A_ID, map2.entries[0].payloadObjectId)
    assertEquals(PAYLOAD_CLASS_NAME, map2.entries[0].payloadClassName)
    assertNull(map2.entries[1].payloadObjectId)
    assertNull(map2.entries[1].payloadClassName)

    val arrayMap = extraction.arrayMapInstances.single()
    assertEquals(ARRAY_MAP_OBJECT_ID, arrayMap.objectId)
    assertEquals(VersionedPayloadMapLayout.ARRAY, arrayMap.layout)
    assertEquals(PersistentSyntaxTreeHprofProcessor.ARRAY_VERSIONED_PAYLOAD_MAP_CLASS_NAME, arrayMap.className)
    assertEquals(listOf(1L, 3L, 5L), arrayMap.entries.map { it.version })
    assertEquals(listOf(PAYLOAD_A_ID, null, PAYLOAD_B_ID), arrayMap.entries.map { it.payloadObjectId })
    assertEquals(listOf(PAYLOAD_CLASS_NAME, null, PAYLOAD_CLASS_NAME), arrayMap.entries.map { it.payloadClassName })
  }

  @Test
  fun `calculates retained overhead of stale payload versions`(@TempDir tempDir: Path) {
    val hprofPath = tempDir.resolve("persistent-syntax-tree-overhead.hprof")
    writeOverheadHprof(hprofPath)

    val analysis = runBlocking { PersistentSyntaxTreeHprofProcessor.analyzePersistentSyntaxTreeOverhead(hprofPath) }

    assertEquals(3, analysis.extraction.allInstances.size)
    assertEquals(setOf(STALE_ONLY_ARRAY_MAP_OBJECT_ID), analysis.staleOnlyMapObjectIds)
    assertEquals(3, analysis.staleRootCount)
    assertEquals(2, analysis.liveRootCount)
    assertEquals(
      setOf(
        ROOT_STALE_PAYLOAD_ID,
        RETAINED_LEAF_ID,
        STALE_ONLY_ARRAY_MAP_OBJECT_ID,
        STALE_ONLY_VERSIONS_ARRAY_ID,
        STALE_ONLY_PAYLOADS_ARRAY_ID,
        STALE_ONLY_STALE_PAYLOAD_ID,
        STALE_ONLY_LATEST_PAYLOAD_ID,
        LIVE_NESTED_STALE_PAYLOAD_ID,
      ),
      analysis.retainedObjectIds,
    )
    assertEquals(8, analysis.retainedObjectCount)
    assertEquals(224, analysis.retainedOverheadBytes)

    assertEquals(
      listOf(
        PersistentSyntaxTreeOverheadClassStats(PAYLOAD_WITH_REFERENCES_CLASS_NAME, 4, 128),
        PersistentSyntaxTreeOverheadClassStats("[J", 1, 32),
        PersistentSyntaxTreeOverheadClassStats("[Ljava.lang.Object;", 1, 32),
        PersistentSyntaxTreeOverheadClassStats(PersistentSyntaxTreeHprofProcessor.ARRAY_VERSIONED_PAYLOAD_MAP_CLASS_NAME, 1, 24),
        PersistentSyntaxTreeOverheadClassStats(LEAF_CLASS_NAME, 1, 8),
      ),
      analysis.retainedObjectsByClass,
    )
  }

  @Test
  fun `calculates retained overhead with custom object layout`(@TempDir tempDir: Path) {
    val hprofPath = tempDir.resolve("persistent-syntax-tree-overhead.hprof")
    writeOverheadHprof(hprofPath)

    val analysis = runBlocking {
      PersistentSyntaxTreeHprofProcessor.analyzePersistentSyntaxTreeOverhead(
        hprofPath,
        objectSizeLayout = HprofObjectSizeLayout(referenceSize = 4),
      )
    }

    assertEquals(176, analysis.retainedOverheadBytes)
    assertEquals(
      listOf(
        PersistentSyntaxTreeOverheadClassStats(PAYLOAD_WITH_REFERENCES_CLASS_NAME, 4, 96),
        PersistentSyntaxTreeOverheadClassStats("[J", 1, 32),
        PersistentSyntaxTreeOverheadClassStats("[Ljava.lang.Object;", 1, 24),
        PersistentSyntaxTreeOverheadClassStats(PersistentSyntaxTreeHprofProcessor.ARRAY_VERSIONED_PAYLOAD_MAP_CLASS_NAME, 1, 16),
        PersistentSyntaxTreeOverheadClassStats(LEAF_CLASS_NAME, 1, 8),
      ),
      analysis.retainedObjectsByClass,
    )
  }

  private fun writeExtractionHprof(path: Path) {
    DataOutputStream(Files.newOutputStream(path)).use { output ->
      val writer = HprofWriter(output, ID_SIZE, 0)
      val fixture = HprofFixture(writer)

      fixture.defineClass(OBJECT_CLASS_ID, "java/lang/Object")
      fixture.defineClass(CLASS_CLASS_ID, "java/lang/Class", superClassObjectId = OBJECT_CLASS_ID)
      fixture.defineClass(LONG_ARRAY_CLASS_ID, "[J", superClassObjectId = OBJECT_CLASS_ID)
      fixture.defineClass(OBJECT_ARRAY_CLASS_ID, "[Ljava/lang/Object;", superClassObjectId = OBJECT_CLASS_ID)
      fixture.defineClass(PAYLOAD_CLASS_ID, PAYLOAD_CLASS_NAME.replace('.', '/'), superClassObjectId = OBJECT_CLASS_ID)
      fixture.defineClass(
        MAP1_CLASS_ID,
        PersistentSyntaxTreeHprofProcessor.VERSIONED_PAYLOAD_MAP1_CLASS_NAME.replace('.', '/'),
        superClassObjectId = OBJECT_CLASS_ID,
        fields = listOf(
          fixture.field("version", Type.LONG),
          fixture.field("payload", Type.OBJECT),
        ),
      )
      fixture.defineClass(
        MAP2_CLASS_ID,
        PersistentSyntaxTreeHprofProcessor.VERSIONED_PAYLOAD_MAP2_CLASS_NAME.replace('.', '/'),
        superClassObjectId = OBJECT_CLASS_ID,
        fields = listOf(
          fixture.field("version1", Type.LONG),
          fixture.field("payload1", Type.OBJECT),
          fixture.field("version2", Type.LONG),
          fixture.field("payload2", Type.OBJECT),
        ),
      )
      fixture.defineClass(
        ARRAY_MAP_CLASS_ID,
        PersistentSyntaxTreeHprofProcessor.ARRAY_VERSIONED_PAYLOAD_MAP_CLASS_NAME.replace('.', '/'),
        superClassObjectId = OBJECT_CLASS_ID,
        fields = listOf(
          fixture.field("versions", Type.OBJECT),
          fixture.field("payloads", Type.OBJECT),
        ),
      )

      writer.writeInstanceDump(PAYLOAD_A_ID, 0, PAYLOAD_CLASS_ID, ByteArray(0))
      writer.writeInstanceDump(PAYLOAD_B_ID, 0, PAYLOAD_CLASS_ID, ByteArray(0))
      writer.writeInstanceDump(
        MAP1_OBJECT_ID,
        0,
        MAP1_CLASS_ID,
        bytes {
          writeLong(7L)
          writeLong(PAYLOAD_B_ID)
        },
      )
      writer.writeInstanceDump(
        MAP2_OBJECT_ID,
        0,
        MAP2_CLASS_ID,
        bytes {
          writeLong(10L)
          writeLong(PAYLOAD_A_ID)
          writeLong(20L)
          writeLong(0L)
        },
      )
      writer.writePrimitiveArrayDump(VERSIONS_ARRAY_ID, 0, Type.LONG, longArrayBytes(1L, 3L, 5L), 3)
      writer.writeObjectArrayDump(PAYLOADS_ARRAY_ID, 0, OBJECT_ARRAY_CLASS_ID, longArrayOf(PAYLOAD_A_ID, 0L, PAYLOAD_B_ID))
      writer.writeInstanceDump(
        ARRAY_MAP_OBJECT_ID,
        0,
        ARRAY_MAP_CLASS_ID,
        bytes {
          writeLong(VERSIONS_ARRAY_ID)
          writeLong(PAYLOADS_ARRAY_ID)
        },
      )

      writer.flushHeapObjects()
    }
  }

  private fun writeOverheadHprof(path: Path) {
    DataOutputStream(Files.newOutputStream(path)).use { output ->
      val writer = HprofWriter(output, ID_SIZE, 0)
      val fixture = HprofFixture(writer)

      fixture.defineClass(OBJECT_CLASS_ID, "java/lang/Object")
      fixture.defineClass(CLASS_CLASS_ID, "java/lang/Class", superClassObjectId = OBJECT_CLASS_ID)
      fixture.defineClass(LONG_ARRAY_CLASS_ID, "[J", superClassObjectId = OBJECT_CLASS_ID)
      fixture.defineClass(OBJECT_ARRAY_CLASS_ID, "[Ljava/lang/Object;", superClassObjectId = OBJECT_CLASS_ID)
      fixture.defineClass(LEAF_CLASS_ID, LEAF_CLASS_NAME.replace('.', '/'), superClassObjectId = OBJECT_CLASS_ID)
      fixture.defineClass(
        PAYLOAD_WITH_REFERENCES_CLASS_ID,
        PAYLOAD_WITH_REFERENCES_CLASS_NAME.replace('.', '/'),
        superClassObjectId = OBJECT_CLASS_ID,
        fields = listOf(
          fixture.field("first", Type.OBJECT),
          fixture.field("second", Type.OBJECT),
          fixture.field("third", Type.OBJECT),
        ),
      )
      fixture.defineClass(
        MAP2_CLASS_ID,
        PersistentSyntaxTreeHprofProcessor.VERSIONED_PAYLOAD_MAP2_CLASS_NAME.replace('.', '/'),
        superClassObjectId = OBJECT_CLASS_ID,
        fields = listOf(
          fixture.field("version1", Type.LONG),
          fixture.field("payload1", Type.OBJECT),
          fixture.field("version2", Type.LONG),
          fixture.field("payload2", Type.OBJECT),
        ),
      )
      fixture.defineClass(
        ARRAY_MAP_CLASS_ID,
        PersistentSyntaxTreeHprofProcessor.ARRAY_VERSIONED_PAYLOAD_MAP_CLASS_NAME.replace('.', '/'),
        superClassObjectId = OBJECT_CLASS_ID,
        fields = listOf(
          fixture.field("versions", Type.OBJECT),
          fixture.field("payloads", Type.OBJECT),
        ),
      )

      writer.writeInstanceDump(RETAINED_LEAF_ID, 0, LEAF_CLASS_ID, ByteArray(0))
      writer.writeInstanceDump(SHARED_LEAF_ID, 0, LEAF_CLASS_ID, ByteArray(0))
      writer.writeInstanceDump(ROOT_STALE_PAYLOAD_ID, 0, PAYLOAD_WITH_REFERENCES_CLASS_ID,
                               objectReferences(RETAINED_LEAF_ID, SHARED_LEAF_ID, STALE_ONLY_ARRAY_MAP_OBJECT_ID))
      writer.writeInstanceDump(ROOT_LATEST_PAYLOAD_ID, 0, PAYLOAD_WITH_REFERENCES_CLASS_ID,
                               objectReferences(SHARED_LEAF_ID, LIVE_NESTED_MAP_OBJECT_ID, 0L))
      writer.writeInstanceDump(STALE_ONLY_STALE_PAYLOAD_ID, 0, PAYLOAD_WITH_REFERENCES_CLASS_ID, objectReferences(0L, 0L, 0L))
      writer.writeInstanceDump(STALE_ONLY_LATEST_PAYLOAD_ID, 0, PAYLOAD_WITH_REFERENCES_CLASS_ID, objectReferences(0L, 0L, 0L))
      writer.writeInstanceDump(LIVE_NESTED_STALE_PAYLOAD_ID, 0, PAYLOAD_WITH_REFERENCES_CLASS_ID, objectReferences(0L, 0L, 0L))
      writer.writeInstanceDump(LIVE_NESTED_LATEST_PAYLOAD_ID, 0, PAYLOAD_WITH_REFERENCES_CLASS_ID, objectReferences(0L, 0L, 0L))

      writer.writePrimitiveArrayDump(STALE_ONLY_VERSIONS_ARRAY_ID, 0, Type.LONG, longArrayBytes(1L, 2L), 2)
      writer.writeObjectArrayDump(
        STALE_ONLY_PAYLOADS_ARRAY_ID,
        0,
        OBJECT_ARRAY_CLASS_ID,
        longArrayOf(STALE_ONLY_STALE_PAYLOAD_ID, STALE_ONLY_LATEST_PAYLOAD_ID),
      )
      writer.writeInstanceDump(
        STALE_ONLY_ARRAY_MAP_OBJECT_ID,
        0,
        ARRAY_MAP_CLASS_ID,
        bytes {
          writeLong(STALE_ONLY_VERSIONS_ARRAY_ID)
          writeLong(STALE_ONLY_PAYLOADS_ARRAY_ID)
        },
      )
      writer.writeInstanceDump(
        LIVE_NESTED_MAP_OBJECT_ID,
        0,
        MAP2_CLASS_ID,
        map2Bytes(1L, LIVE_NESTED_STALE_PAYLOAD_ID, 2L, LIVE_NESTED_LATEST_PAYLOAD_ID),
      )
      writer.writeInstanceDump(
        ROOT_MAP_OBJECT_ID,
        0,
        MAP2_CLASS_ID,
        map2Bytes(10L, ROOT_STALE_PAYLOAD_ID, 20L, ROOT_LATEST_PAYLOAD_ID),
      )

      writer.flushHeapObjects()
    }
  }

  private class HprofFixture(private val writer: HprofWriter) {
    private var nextStringId = 1_000L
    private var nextClassSerialNumber = 1

    fun field(name: String, type: Type): InstanceFieldEntry = InstanceFieldEntry(stringId(name), type)

    fun defineClass(
      classObjectId: Long,
      className: String,
      superClassObjectId: Long = 0,
      fields: List<InstanceFieldEntry> = emptyList(),
    ) {
      writer.writeLoadClass(nextClassSerialNumber++, classObjectId, 0, stringId(className))
      writer.writeClassDump(
        classObjectId,
        0,
        superClassObjectId,
        0,
        0,
        0,
        fields.sumOf { if (it.type == Type.OBJECT) ID_SIZE else it.type.size },
        emptyArray<ConstantPoolEntry>(),
        emptyArray<StaticFieldEntry>(),
        fields.toTypedArray(),
      )
    }

    private fun stringId(value: String): Long {
      val id = nextStringId++
      writer.writeStringInUTF8(id, value)
      return id
    }
  }

  private fun bytes(block: DataOutputStream.() -> Unit): ByteArray {
    val byteStream = ByteArrayOutputStream()
    DataOutputStream(byteStream).use(block)
    return byteStream.toByteArray()
  }

  private fun longArrayBytes(vararg values: Long): ByteArray = bytes {
    for (value in values) {
      writeLong(value)
    }
  }

  private fun objectReferences(vararg objectIds: Long): ByteArray = bytes {
    for (objectId in objectIds) {
      writeLong(objectId)
    }
  }

  private fun map2Bytes(version1: Long, payload1ObjectId: Long, version2: Long, payload2ObjectId: Long): ByteArray = bytes {
    writeLong(version1)
    writeLong(payload1ObjectId)
    writeLong(version2)
    writeLong(payload2ObjectId)
  }

  private companion object {
    const val ID_SIZE: Int = 8

    const val OBJECT_CLASS_ID: Long = 1
    const val CLASS_CLASS_ID: Long = 2
    const val LONG_ARRAY_CLASS_ID: Long = 3
    const val OBJECT_ARRAY_CLASS_ID: Long = 4
    const val PAYLOAD_CLASS_ID: Long = 5
    const val MAP2_CLASS_ID: Long = 6
    const val ARRAY_MAP_CLASS_ID: Long = 7
    const val PAYLOAD_WITH_REFERENCES_CLASS_ID: Long = 8
    const val LEAF_CLASS_ID: Long = 9
    const val MAP1_CLASS_ID: Long = 10

    const val PAYLOAD_A_ID: Long = 101
    const val PAYLOAD_B_ID: Long = 102
    const val MAP2_OBJECT_ID: Long = 103
    const val VERSIONS_ARRAY_ID: Long = 104
    const val PAYLOADS_ARRAY_ID: Long = 105
    const val ARRAY_MAP_OBJECT_ID: Long = 106
    const val MAP1_OBJECT_ID: Long = 107

    const val ROOT_MAP_OBJECT_ID: Long = 201
    const val ROOT_STALE_PAYLOAD_ID: Long = 202
    const val ROOT_LATEST_PAYLOAD_ID: Long = 203
    const val RETAINED_LEAF_ID: Long = 204
    const val SHARED_LEAF_ID: Long = 205
    const val STALE_ONLY_ARRAY_MAP_OBJECT_ID: Long = 206
    const val STALE_ONLY_VERSIONS_ARRAY_ID: Long = 207
    const val STALE_ONLY_PAYLOADS_ARRAY_ID: Long = 208
    const val STALE_ONLY_STALE_PAYLOAD_ID: Long = 209
    const val STALE_ONLY_LATEST_PAYLOAD_ID: Long = 210
    const val LIVE_NESTED_MAP_OBJECT_ID: Long = 211
    const val LIVE_NESTED_STALE_PAYLOAD_ID: Long = 212
    const val LIVE_NESTED_LATEST_PAYLOAD_ID: Long = 213

    const val PAYLOAD_CLASS_NAME: String = "test.Payload"
    const val PAYLOAD_WITH_REFERENCES_CLASS_NAME: String = "test.PayloadWithReferences"
    const val LEAF_CLASS_NAME: String = "test.Leaf"
  }
}
