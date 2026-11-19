// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.AbstractEntityStorage
import com.intellij.platform.workspace.storage.impl.ClassToIntConverterImpl
import com.intellij.platform.workspace.storage.impl.findWorkspaceEntity
import com.intellij.platform.workspace.storage.impl.forgetWorkspaceEntityClasses
import com.intellij.platform.workspace.storage.impl.toClassId
import com.intellij.platform.workspace.storage.testEntities.entities.ChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.ParentEntity
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Callable
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ClassToIntConverterTest {
  @Test
  fun `multi thread initialization`() {
    val random = Random.Default
    repeat(1_000) {
      val converter = ClassToIntConverterImpl()
      val threads = List(10) {
        Callable {
          repeat(10) {
            val randomClass = classes.random(random)
            converter.getInt(randomClass.javaClass)
          }
          // Make sure that every class was initialized
          classes.forEach { converter.getInt(it.javaClass) }
        }
      }
      val service = AppExecutorUtil.createBoundedApplicationPoolExecutor("Test executor", 4)
      ConcurrencyUtil.invokeAll(threads, service)

      val res = classes.map { converter.getInt(it.javaClass) }.sorted()
      assertEquals(List(20) { it }, res)
    }
  }

  @Test
  fun `a removed class frees its slot and keeps the other ids`() {
    val converter = ClassToIntConverterImpl()
    assertEquals(0, converter.getInt(MyClass1.javaClass))
    assertEquals(1, converter.getInt(MyClass2.javaClass))

    converter.removeIf { it == MyClass1.javaClass }

    assertEquals(MyClass2.javaClass, converter.getClassOrDie(1))
    assertThrows<IllegalStateException> { converter.getClassOrDie(0) }
  }

  @Test
  fun `a new class never takes a freed id`() {
    val converter = ClassToIntConverterImpl()
    converter.getInt(MyClass1.javaClass)
    converter.getInt(MyClass2.javaClass)
    converter.removeIf { it == MyClass1.javaClass }

    assertEquals(2, converter.getInt(MyClass3.javaClass))
  }

  @Test
  fun `a removed class gets a new id when it comes back`() {
    val converter = ClassToIntConverterImpl()
    converter.getInt(MyClass1.javaClass)
    converter.getInt(MyClass2.javaClass)
    converter.removeIf { it == MyClass1.javaClass }
    converter.getInt(MyClass3.javaClass)

    assertEquals(3, converter.getInt(MyClass1.javaClass))
  }

  @Test
  fun `a class that no live storage holds is forgotten`() {
    val classId = MyClass1.javaClass.toClassId()

    forgetWorkspaceEntityClasses(listOf(createEmptyBuilder().toSnapshot())) { it == MyClass1.javaClass }

    assertThrows<IllegalStateException> { classId.findWorkspaceEntity() }
  }

  @Test
  fun `a class with an entity in a live storage stays registered`() {
    val builder = createEmptyBuilder()
    builder addEntity ParentEntity("parent", MySource)
    val classId = ParentEntity::class.java.toClassId()

    forgetWorkspaceEntityClasses(listOf(builder.toSnapshot())) { it == ParentEntity::class.java }

    assertEquals<Class<*>>(ParentEntity::class.java, classId.findWorkspaceEntity())
  }

  @Test
  fun `a class that only a reference of a live storage names stays registered`() {
    val builder = createEmptyBuilder()
    val child = builder addEntity ChildEntity("child", MySource) {
      parentEntity = ParentEntity("parent", MySource)
    }
    builder.removeEntity(child)
    val snapshot = builder.toSnapshot()
    assertNotNull((snapshot as AbstractEntityStorage).refs.findConnectionId(ParentEntity::class.java, ChildEntity::class.java))
    val classId = ChildEntity::class.java.toClassId()

    forgetWorkspaceEntityClasses(listOf(snapshot)) { it == ChildEntity::class.java }

    assertEquals<Class<*>>(ChildEntity::class.java, classId.findWorkspaceEntity())
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
