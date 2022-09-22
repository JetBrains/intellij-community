/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof

import com.intellij.diagnostic.hprof.analysis.AnalysisConfig
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.ref.WeakReference

class HeapAnalysisTest {

  private val tmpFolder: TemporaryFolder = TemporaryFolder()
  private var remapInMemory: Boolean = true

  @Before
  fun setUp() {
    tmpFolder.create()
  }

  @After
  fun tearDown() {
    tmpFolder.delete()
  }

  private fun runHProfScenario(scenario: HProfBuilder.() -> Unit,
                               baselineFileName: String,
                               nominatedClassNames: List<String>? = null,
                               classNameMapping: ((Class<*>) -> String)? = null) {
    object : HProfScenarioRunner(tmpFolder, remapInMemory) {
      override fun mapClassName(clazz: Class<*>): String = classNameMapping?.invoke(clazz) ?: super.mapClassName(clazz)
    }.run(scenario, baselineFileName, nominatedClassNames)
  }

  @Test
  fun testPathsThroughDifferentFields() {
    class MyRef(val referent: Any)
    class TestString(val s: String)
    class TestClassB(private val b1string: TestString, private val b2string: TestString)
    class TestClassA {
      private val a1string = TestString("TestString")
      private val a2b = TestClassB(a1string, TestString("TestString2"))
      private val a3b = TestClassB(a1string, TestString("TestString3"))
    }

    ReferenceStore().use { refStore ->
      val scenario: HProfBuilder.() -> Unit = {
        val a = TestClassA()
        addRootGlobalJNI(listOf(MyRef(MyRef(MyRef(a))),
                                TestClassA(),
                                refStore.createWeakReference(TestClassA()),
                                refStore.createWeakReference(a)))
        addRootUnknown(TestClassA())
      }
      runHProfScenario(scenario, "testPathsThroughDifferentFields.txt",
                       listOf("TestClassB",
                              "TestClassA",
                              "TestString"))
    }
  }

  @Test
  fun testClassNameClash() {
    class MyTestClass1
    class MyTestClass2

    val scenario: HProfBuilder.() -> Unit = {
      addRootUnknown(MyTestClass1())
      addRootGlobalJNI(MyTestClass2())

    }
    val classNameMapping: (Class<*>) -> String = { c ->
      if (c == MyTestClass1::class.java ||
          c == MyTestClass2::class.java) {
        "MyTestClass"
      }
      else {
        c.name
      }
    }

    runHProfScenario(scenario, "testClassNameClash.txt",
                     listOf("MyTestClass!1",
                            "MyTestClass!2"),
                     classNameMapping)
  }

  @Test
  fun testJavaFrameGCRootPriority() {
    class C1
    class C2

    val scenario: HProfBuilder.() -> Unit = {
      val o1 = C1()
      val o2 = C2()
      addRootUnknown(o1)
      val threadSerialNumber = addStackTrace(Thread.currentThread(), 2)
      // This java frame should be overshadowed by root unknown
      addRootJavaFrame(o1, threadSerialNumber, 1)
      // This objects sole reference is from a frame
      addRootJavaFrame(o2, threadSerialNumber, 1)
    }
    runHProfScenario(scenario, "testJavaFrameGCRootPriority.txt",
                     listOf("C1", "C2"))
  }

  @Test
  fun testIgnoreRootWithNoMatchingObject() {
    class C1

    val scenario: HProfBuilder.() -> Unit = {
      addRootUnknown(C1())
      internalWriter.writeRootUnknown(999_999_999) // id without matching object
    }
    remapInMemory = false
    runHProfScenario(scenario, "testIgnoreRootWithNoMatchingObject.txt",
                     listOf("C1"))
  }

  @Test
  fun testDisposerTree() {
    val objectTree = ObjectTreeTestWrapper()

    open class MyDisposable : Disposable {
      override fun dispose() {
      }
    }

    class MyDisposableRoot : MyDisposable()
    class MyDisposableChild : MyDisposable()
    class MyDisposableGrandchild : MyDisposable()

    val root = MyDisposableRoot()
    val child1 = MyDisposableChild()
    val child2 = MyDisposableChild()
    val largeList = (1..1000).map { MyDisposableGrandchild() }

    objectTree.register(root, child1)
    objectTree.register(root, MyDisposableChild())
    objectTree.register(root, child2)

    objectTree.register(child1, MyDisposableGrandchild())
    for (grandchild in largeList) {
      objectTree.register(child2, grandchild)
    }
    objectTree.register(root, Disposer.newDisposable())

    objectTree.register(Disposer.newDisposable(), MyDisposableChild())

    val scenario: HProfBuilder.() -> Unit = {
      addDisposer(this, objectTree)
    }
    object : HProfScenarioRunner(tmpFolder, remapInMemory) {
      override fun adjustConfig(config: AnalysisConfig): AnalysisConfig = configWithDisposerTreeOnly()
    }.run(scenario, "testDisposerTree.txt", null)
  }

  @Test
  fun testDominatorTreeFlameGraph() {
    val scenario: HProfBuilder.() -> Unit = {
      abstract class N
      class A(val b: N, val c : N, val d: N): N()
      class B(val e: N, val f: N): N()
      class C(val f: N): N()
      class D(): N()
      class E(val g: N): N()
      class F: N()
      class G(var b: N?): N()
      val g = G(null)
      val f = F()
      val e = E(g)
      val d = D()
      val c = C(f)
      val b = B(e, f)
      val a = A(b, c, d)
      g.b = b
      addRootGlobalJNI(a)
    }
    object : HProfScenarioRunner(tmpFolder, remapInMemory) {
      override fun adjustConfig(config: AnalysisConfig): AnalysisConfig = configWithDominatorTreeOnly()
    }.run(scenario, "testDominatorTreeFlameGraph.txt", null)
  }

  private fun configWithDisposerTreeOnly() = AnalysisConfig(
    AnalysisConfig.PerClassOptions(
      classNames = listOf(),
      includeClassList = false,
    ),
    AnalysisConfig.HistogramOptions(includeByCount = false,
                                    includeBySize = false,
                                    includeSummary = false),
    AnalysisConfig.DisposerOptions(
      includeDisposerTree = true,
      includeDisposerTreeSummary = true,
      includeDisposedObjectsSummary = false,
      includeDisposedObjectsDetails = false,
      disposerTreeSummaryOptions = AnalysisConfig.DisposerTreeSummaryOptions(
        nodeCutoff = 0
      )
    ),
    dominatorTreeOptions = AnalysisConfig.DominatorTreeOptions(
      includeDominatorTree = false,
    )
  )

  private fun configWithDominatorTreeOnly() = AnalysisConfig(
    AnalysisConfig.PerClassOptions(
      classNames = listOf(),
      includeClassList = false,
    ),
    AnalysisConfig.HistogramOptions(includeByCount = false,
                                    includeBySize = false,
                                    includeSummary = false),
    AnalysisConfig.DisposerOptions(
      includeDisposerTree = false,
      includeDisposerTreeSummary = false,
      includeDisposedObjectsSummary = false,
      includeDisposedObjectsDetails = false,
      disposerTreeSummaryOptions = AnalysisConfig.DisposerTreeSummaryOptions(
        nodeCutoff = 0
      )
    ),
    dominatorTreeOptions = AnalysisConfig.DominatorTreeOptions(
      includeDominatorTree = true,
      minNodeSize = 0
    )
  )


  /**
   * Adds disposer to the hprof. Disposer is simplified and contains only ourTree static field, all other static fields are omitted.
   *
   * @param objectTree @code{ObjectTreeTestWrapper} which replaces @code{Disposer.ourTree}
   */
  private fun addDisposer(builder: HProfBuilder, objectTree: ObjectTreeTestWrapper) {
    class DisposerStatics {
      @JvmField
      var ourTree = objectTree.getTree()
    }
    builder.registerStaticsForClass(Disposer::class.java, DisposerStatics())
    builder.addObject(Disposer::class.java)
  }
}


/**
 * Helper class to keep strong references to objects referenced by newly created weak references.
 */
private class ReferenceStore : AutoCloseable {
  private val set = HashSet<Any?>()

  fun <T> createWeakReference(obj: T): WeakReference<T> {
    set.add(obj)
    return WeakReference(obj)
  }

  override fun close() {
    set.clear()
  }
}
