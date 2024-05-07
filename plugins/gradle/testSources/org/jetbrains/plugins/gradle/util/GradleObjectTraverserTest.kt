// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Suppress("unused")
class GradleObjectTraverserTest {

  @Test
  fun `test traversing simple object`() {
    class NestedData1
    class NestedData2
    class Data(val a: NestedData1, val b: NestedData2)

    val aRootObject = Data(NestedData1(), NestedData2())

    val result = ArrayList<String>()
    GradleObjectTraverser().walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf("Data", "NestedData1", "NestedData2"), result)
  }

  @Test
  fun `test traversing object with private fields`() {
    class NestedData1
    class NestedData2
    class Data(private val a: NestedData1, private val b: NestedData2)

    val aRootObject = Data(NestedData1(), NestedData2())

    val result = ArrayList<String>()
    GradleObjectTraverser().walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf("Data", "NestedData1", "NestedData2"), result)
  }

  @Test
  fun `test traversing object with primitives`() {
    class NestedData1(val a: Int)
    class NestedData2(val a: Float)
    class Data(val a: NestedData1, val b: NestedData2)

    val aRootObject = Data(NestedData1(1), NestedData2(1.0f))

    val result = ArrayList<String>()
    GradleObjectTraverser().walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf("Data", "NestedData1", "NestedData2"), result)
  }

  @Test
  fun `test traversing collections with objects`() {
    class UniqueData

    val aRootObject = ArrayList<UniqueData>().apply {
      add(UniqueData())
      add(UniqueData())
      add(UniqueData())
    }

    val result = ArrayList<String>()
    GradleObjectTraverser().walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf("ArrayList", "UniqueData", "UniqueData", "UniqueData"), result)
  }

  @Test
  fun `test traversing collections with wrapped primitives`() {
    val aRootObject = ArrayList<Int>().apply {
      add(1)
      add(2)
      add(3)
    }

    val result = ArrayList<String>()
    GradleObjectTraverser().walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf("ArrayList"), result)
  }

  @Test
  fun `test traversing maps with objects`() {
    class UniqueKey
    class UniqueValue

    val aRootObject = LinkedHashMap<UniqueKey, UniqueValue>().apply {
      put(UniqueKey(), UniqueValue())
      put(UniqueKey(), UniqueValue())
      put(UniqueKey(), UniqueValue())
    }

    val result = ArrayList<String>()
    GradleObjectTraverser().walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf(
      "LinkedHashMap",
      "UniqueKey", "UniqueValue",
      "UniqueKey", "UniqueValue",
      "UniqueKey", "UniqueValue"
    ), result)
  }

  @Test
  fun `test traversing maps with wrapped primitives`() {
    val aRootObject = LinkedHashMap<Int, Char>().apply {
      put(1, 'a')
      put(2, 'b')
      put(3, 'c')
    }

    val result = ArrayList<String>()
    GradleObjectTraverser().walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf("LinkedHashMap"), result)
  }

  @Test
  fun `test traversing objects with excluded classes`() {
    class NestedData1
    class NestedData2
    class Data(val a: NestedData1, val b: NestedData2)

    val aRootObject = Data(NestedData1(), NestedData2())

    val result = ArrayList<String>()
    GradleObjectTraverser(classesToSkip = setOf(NestedData1::class.java)).walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf("Data", "NestedData2"), result)
  }

  @Test
  fun `test traversing objects with excluded classes for root`() {
    class NestedData1
    class NestedData2
    class Data(val a: NestedData1, val b: NestedData2)

    val aRootObject = Data(NestedData1(), NestedData2())

    val result = ArrayList<String>()
    GradleObjectTraverser(classesToSkip = setOf(Data::class.java)).walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf<String>(), result)
  }

  @Test
  fun `test traversing objects with excluded classes for children`() {
    class NestedData1
    class NestedData2
    class Data(val a: NestedData1, val b: NestedData2)

    val aRootObject = Data(NestedData1(), NestedData2())

    val result = ArrayList<String>()
    GradleObjectTraverser(classesToSkipChildren = setOf(Data::class.java)).walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf("Data"), result)
  }

  @Test
  fun `test traversing objects with super fields`() {
    class NestedData1
    class NestedData2
    abstract class AbstractData(val a: NestedData1)
    class Data(a: NestedData1, val b: NestedData2) : AbstractData(a)

    val aRootObject = Data(NestedData1(), NestedData2())

    val result = ArrayList<String>()
    GradleObjectTraverser().walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf("Data", "NestedData2", "NestedData1"), result)
  }

  @Test
  fun `test traversing objects with duplicates`() {
    class NestedData
    class Data(val a: NestedData, val b: NestedData, val c: NestedData)

    val aNestedObject = NestedData()
    val aRootObject = Data(aNestedObject, aNestedObject, aNestedObject)

    val result = ArrayList<String>()
    GradleObjectTraverser().walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf("Data", "NestedData"), result)
  }

  @Test
  fun `test traversing objects with cycles`() {
    class Root(val children: MutableList<Any>)
    class Node(val children: MutableList<Any>)

    val aRootObject = Root(ArrayList())
    val aNestedObject = Node(ArrayList())

    aRootObject.children.add(aRootObject)
    aRootObject.children.add(aRootObject)
    aRootObject.children.add(aNestedObject)
    aNestedObject.children.add(aRootObject)

    val result = ArrayList<String>()
    GradleObjectTraverser().walk(aRootObject) { anObject ->
      result.add(anObject.javaClass.simpleName)
    }
    Assertions.assertEquals(listOf("Root", "ArrayList", "Node", "ArrayList"), result)
  }
}