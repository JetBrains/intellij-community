// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.hprof.util

import com.intellij.diagnostic.hprof.classstore.ClassDefinition
import com.intellij.diagnostic.hprof.classstore.ClassStore
import com.intellij.diagnostic.hprof.classstore.InstanceField
import com.intellij.diagnostic.hprof.classstore.StaticField
import com.intellij.diagnostic.hprof.parser.Type
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RefIndexUtilTest {

  @Test
  fun `returns a fallback field description when ref index is out of range`() {
    val classStore = createClassStore()

    assertEquals("(field_21)", RefIndexUtil.getFieldDescription(21, classStore.classClass, classStore))
  }

  @Test
  fun `returns the synthetic class field name for a valid class reference`() {
    val classStore = createClassStore()
    val refHolderClass = classStore["com.example.RefHolder"]

    assertEquals("<class>", RefIndexUtil.getFieldDescription(2, refHolderClass, classStore))
  }

  private fun createClassStore(): ClassStore {
    val classes = Long2ObjectOpenHashMap<ClassDefinition>()
    classes.put(1, createClassDefinition("java.lang.ref.SoftReference", 1))
    classes.put(2, createClassDefinition("java.lang.ref.WeakReference", 2))
    classes.put(3, createClassDefinition("java.lang.Class", 3))
    classes.put(4, createClassDefinition("com.example.RefHolder", 4, refFieldNames = arrayOf("payload")))
    return ClassStore(classes)
  }

  private fun createClassDefinition(
    name: String,
    id: Long,
    refFieldNames: Array<String> = emptyArray(),
  ): ClassDefinition {
    val refFields = Array(refFieldNames.size) { index ->
      InstanceField(refFieldNames[index], index, Type.OBJECT)
    }
    return ClassDefinition(
      name = name,
      id = id,
      superClassId = 0,
      classLoaderId = 0,
      instanceSize = 0,
      superClassOffset = 0,
      refInstanceFields = refFields,
      primitiveInstanceFields = emptyArray(),
      constantFields = LongArray(0),
      objectStaticFields = emptyArray<StaticField>(),
      primitiveStaticFields = emptyArray(),
    )
  }
}
