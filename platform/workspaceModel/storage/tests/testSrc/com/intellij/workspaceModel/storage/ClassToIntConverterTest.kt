// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.workspaceModel.storage.impl.ClassToIntConverter
import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.util.concurrent.Callable
import kotlin.random.Random

class ClassToIntConverterTest {
  @Test
  fun `multy thread initialization`() {
    val random = Random.Default
    repeat(1_000) {
      val converter = ClassToIntConverter()
      val threads = List(10) {
        Callable {
          repeat(10) {
            val randomClass = classes.random(random)
            converter.getInt(randomClass.javaClass)
          }
        }
      }
      val service = AppExecutorUtil.createBoundedApplicationPoolExecutor("Test executor", 1)
      ConcurrencyUtil.invokeAll(threads, service).map { it.get() }

      val res = classes.map { converter.getInt(it.javaClass) }.toSet()
      assertEquals(20, res.size)
    }
  }
}

private val classes = listOf(
  MyClass1,
  MyClass2,
  MyClass3,
  MyClass4,
  MyClass5,
  MyClass6,
  MyClass7,
  MyClass8,
  MyClass9,
  MyClass10,
  MyClass11,
  MyClass12,
  MyClass13,
  MyClass14,
  MyClass15,
  MyClass16,
  MyClass17,
  MyClass18,
  MyClass19,
  MyClass20,
)

private object MyClass1
private object MyClass2
private object MyClass3
private object MyClass4
private object MyClass5
private object MyClass6
private object MyClass7
private object MyClass8
private object MyClass9
private object MyClass10
private object MyClass11
private object MyClass12
private object MyClass13
private object MyClass14
private object MyClass15
private object MyClass16
private object MyClass17
private object MyClass18
private object MyClass19
private object MyClass20
