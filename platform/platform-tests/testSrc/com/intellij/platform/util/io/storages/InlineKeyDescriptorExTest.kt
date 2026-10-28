// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages

import com.intellij.platform.util.io.storages.DataExternalizerEx.KnownSizeRecordWriter
import com.intellij.platform.util.io.storages.PersistentFormatLayouts.JAVA_INT_UNALIGNED
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.InlineKeyDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.foreign.MemorySegment

class InlineKeyDescriptorExTest {
  @Test
  fun integerDescriptorKeepsEveryIntCode() {
    val descriptor = CommonKeyDescriptors.integer()
    for (code in CODES) {
      assertEquals(code, descriptor.toInt(code))
      assertEquals(code, descriptor.fromInt(code))
      assertEquals(code, descriptor.getHashCode(code))
      assertEquals(code, descriptor.read(serialize(descriptor, code)))
    }
  }

  @Test
  fun inlineEqualityUsesTheCodeInsteadOfObjectEquality() {
    val descriptor = KeyDescriptor()
    for (code in CODES) {
      val original = Key(code, "original")
      val decoded = descriptor.fromInt(descriptor.toInt(original))
      assertEquals(code, descriptor.getHashCode(original))
      assertTrue(descriptor.isEqual(original, decoded), "Decoding must restore a key with the same code")
      assertFalse(descriptor.isEqual(original, Key(code.inv(), "original")))
      assertEquals(code, descriptor.toInt(descriptor.read(serialize(descriptor, original))))
    }
  }

  @Test
  fun integerAdapterReusesTheSingleton() {
    assertSame(CommonKeyDescriptors.integer(), KeyDescriptorEx.adapt(EnumeratorIntegerDescriptor.INSTANCE))
    assertSame(CommonKeyDescriptors.integer(), DataExternalizerEx.adapt(EnumeratorIntegerDescriptor.INSTANCE))
  }

  @Test
  fun inlineAdapterUsesFixedNativeIntsForBothStreamFormats() {
    val compact = object : LegacyDescriptor() {
      override fun isCompactFormat(): Boolean = true
    }
    for (original in listOf(LegacyDescriptor(), compact)) {
      val descriptor = KeyDescriptorEx.adapt(original)
      val inline = assertInstanceOf(InlineKeyDescriptorEx::class.java, descriptor)
      val dataAdapter = DataExternalizerEx.adapt(original)
      assertInstanceOf(InlineKeyDescriptorEx::class.java, dataAdapter)
      for (code in CODES) {
        val key = Key(code, "original")
        for (adapter in listOf(descriptor, dataAdapter)) {
          val writer = adapter.writerFor(key)
          val bytes = ByteArray(Int.SIZE_BYTES + 2) { 85 }
          val target = MemorySegment.ofArray(bytes)
          writer.write(target, 1)

          assertEquals(Int.SIZE_BYTES, adapter.recordSizeIfConstant())
          assertEquals(Int.SIZE_BYTES, writer.recordSize())
          assertEquals(code, target.get(JAVA_INT_UNALIGNED, 1), "Both adapters must use fixed native ints, including compact keys")
          assertEquals(code, original.toInt(adapter.read(target.asSlice(1, Int.SIZE_BYTES.toLong()))))
          assertEquals(85.toByte(), bytes.first(), "The writer must preserve bytes before the record")
          assertEquals(85.toByte(), bytes.last(), "The writer must preserve bytes after the record")
        }
        assertEquals(code, descriptor.getHashCode(key))
        assertTrue(descriptor.isEqual(key, original.fromInt(code)), "Adaptation must preserve equality by code")
        assertEquals(code, (inline.fromInt(code) as Key).code)
      }
    }
  }

  @Test
  fun adaptationKeepsTheBilingualDescriptor() {
    val descriptor = object : BilingualInlineKeyDescriptor<Key>() {
      override fun toInt(key: Key): Int = key.code
      override fun fromInt(code: Int): Key = Key(code, "decoded")
      override fun read(input: MemorySegment): Key = fromInt(CommonKeyDescriptors.integer().read(input))
      override fun writerFor(value: Key): KnownSizeRecordWriter = CommonKeyDescriptors.integer().writerFor(value.code)
    }
    assertSame(descriptor, KeyDescriptorEx.adapt(descriptor))
    assertSame(descriptor, DataExternalizerEx.adapt(descriptor))
  }

  private data class Key(val code: Int, val label: String)

  private class KeyDescriptor : InlineKeyDescriptorEx<Key> {
    override fun toInt(key: Key): Int = key.code
    override fun fromInt(code: Int): Key = Key(code, "decoded")
    override fun read(input: MemorySegment): Key = fromInt(CommonKeyDescriptors.integer().read(input))
    override fun writerFor(value: Key): KnownSizeRecordWriter = CommonKeyDescriptors.integer().writerFor(value.code)
  }

  private open class LegacyDescriptor : InlineKeyDescriptor<Key>() {
    override fun toInt(key: Key): Int = key.code
    override fun fromInt(code: Int): Key = Key(code, "decoded")
  }

  private fun <K : Any> serialize(descriptor: DataExternalizerEx<K>, key: K): MemorySegment {
    val writer = descriptor.writerFor(key)
    val target = MemorySegment.ofArray(ByteArray(writer.recordSize()))
    writer.write(target, 0)
    return target
  }

  companion object {
    private val CODES = listOf(0, -1, 1, 63, 192, 0x01020304, Int.MIN_VALUE, Int.MAX_VALUE)
  }
}
