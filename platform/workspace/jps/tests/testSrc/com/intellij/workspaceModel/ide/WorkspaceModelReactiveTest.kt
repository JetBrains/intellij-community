// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.edtWriteAction
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.platform.workspace.storage.query.filterNotNull
import com.intellij.platform.workspace.storage.query.map
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.waitUntil
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.reactive.WmReactive
import kotlinx.coroutines.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@TestApplication
class WorkspaceModelReactiveTest {
  @RegisterExtension
  @JvmField
  val projectModel = ProjectModelExtension()

  private val wm: WorkspaceModelImpl
    get() = projectModel.project.workspaceModel.impl

  val WorkspaceModel.impl: WorkspaceModelImpl get() = this as WorkspaceModelImpl

  private fun <T> MutableList<T>.set(newData: Iterable<T>) {
    this.clear()
    this.addAll(newData)
  }

  @Test
  fun `collect data from query`() {
    runBlocking {
      val collector = ArrayList<String>()
      edtWriteAction {
        wm.updateProjectModel { it addEntity NamedEntity("X", MySource) }
      }
      val rete = WmReactive(wm)
      val query = entities<NamedEntity>().map { it.myName }

      val job = launch {
        rete.flowOfQuery(query).collect {
          collector.set(it)
        }
      }

      waitUntilAllAsserted {
        assertEquals(1, collector.size, collector.toString())
        assertContains(collector, "X")
      }

      edtWriteAction {
        wm.updateProjectModel { it addEntity NamedEntity("Y", MySource) }
      }
      waitUntilAllAsserted {
        assertEquals(2, collector.size)
        assertContains(collector, "X")
        assertContains(collector, "Y")
      }

      edtWriteAction {
        wm.updateProjectModel {
          it addEntity NamedEntity("Z", MySource)
          it addEntity NamedEntity("ZZ", MySource)
          it addEntity NamedEntity("ZZZ", MySource)
        }
      }
      waitUntilAllAsserted {
        assertEquals(5, collector.size)
        assertContains(collector, "X")
        assertContains(collector, "Y")
        assertContains(collector, "Z")
        assertContains(collector, "ZZ")
        assertContains(collector, "ZZZ")
      }

      edtWriteAction {
        wm.updateProjectModel { it addEntity NamedEntity("C", MySource) }
        wm.updateProjectModel { it addEntity NamedEntity("CC", MySource) }
        wm.updateProjectModel { it addEntity NamedEntity("CCC", MySource) }
      }

      waitUntilAllAsserted {
        assertEquals(8, collector.size)
        assertContains(collector, "X")
        assertContains(collector, "Y")
        assertContains(collector, "Z")
        assertContains(collector, "ZZ")
        assertContains(collector, "ZZZ")
        assertContains(collector, "C")
        assertContains(collector, "CC")
        assertContains(collector, "CCC")
      }

      edtWriteAction {
        wm.updateProjectModel { it addEntity SampleEntity2("data", true, MySource) }
      }

      waitUntilAllAsserted {
        assertEquals(8, collector.size)
      }

      edtWriteAction {
        wm.updateProjectModel { it addEntity NamedEntity("A", MySource) }
      }

      waitUntilAllAsserted {
        assertEquals(9, collector.size)
        assertContains(collector, "X")
        assertContains(collector, "Y")
        assertContains(collector, "Z")
        assertContains(collector, "ZZ")
        assertContains(collector, "ZZZ")
        assertContains(collector, "C")
        assertContains(collector, "CC")
        assertContains(collector, "CCC")
        assertContains(collector, "A")
      }

      job.cancelAndJoin()
    }
  }

  @Test
  fun `rename and collect data`() {
    runBlocking {
      val collector = ArrayList<String>()
      edtWriteAction {
        wm.updateProjectModel { it addEntity NamedEntity("X", MySource) }
      }
      val rete = WmReactive(wm)
      val query = entities<NamedEntity>().map { it.myName }

      val job = launch {
        rete.flowOfQuery(query).collect {
          collector.set(it)
        }
      }

      waitUntilAllAsserted {
        assertEquals(1, collector.size)
        assertContains(collector, "X")
      }

      edtWriteAction {
        wm.updateProjectModel {
          val entity = it.resolve(NameId("X"))!!
          it.modifyNamedEntity(entity) {
            myName = "Y"
          }
        }
      }

      waitUntilAllAsserted {
        assertEquals(1, collector.size)
        assertContains(collector, "Y")
      }

      job.cancelAndJoin()
    }
  }

  @Test
  @Disabled("Fails because data from modules cannot be accessed")
  fun `request entities themselves`() {
    runBlocking {
      val collector = ArrayList<NamedEntity>()
      edtWriteAction {
        wm.updateProjectModel { it addEntity NamedEntity("X", MySource) }
      }
      val rete = WmReactive(wm)
      val query = entities<NamedEntity>()

      val job = launch {
        rete.flowOfQuery(query).collect {
          collector.set(it)
        }
      }

      waitUntilAllAsserted {
        assertEquals(1, collector.size)
        assertContains(collector.map { it.myName }, "X")
      }

      edtWriteAction {
        wm.updateProjectModel {
          val entity = it.resolve(NameId("X"))!!
          it.modifyNamedEntity(entity) {
            myName = "Y"
          }
        }
      }

      waitUntilAllAsserted {
        assertEquals(1, collector.size)
        assertContains(collector.map { it.myName }, "Y")
      }

      job.cancelAndJoin()
    }
  }

  @Test
  fun `operations with query`() {
    runBlocking {
      val collector = ArrayList<String>()
      edtWriteAction {
        wm.updateProjectModel { it addEntity NamedEntity("X", MySource) }
      }
      val rete = WmReactive(wm)
      val query = entities<NamedEntity>().map { it.myName }

      val job = launch {
        rete.flowOfNewElements(query).collect {
          collector.add(it)
        }
      }

      waitUntilAllAsserted {
        assertEquals(1, collector.size)
        assertContains(collector, "X")
      }

      edtWriteAction {
        wm.updateProjectModel { it addEntity NamedEntity("Y", MySource) }
      }


      waitUntilAllAsserted {
        assertEquals(2, collector.size)
        assertContains(collector, "X")
        assertContains(collector, "Y")
      }

      edtWriteAction {
        wm.updateProjectModel {
          it addEntity NamedEntity("Z", MySource)
          it addEntity NamedEntity("ZZ", MySource)
          it addEntity NamedEntity("ZZZ", MySource)
        }
      }
      waitUntilAllAsserted {
        assertEquals(5, collector.size)
        assertContains(collector, "X")
        assertContains(collector, "Y")
        assertContains(collector, "Z")
        assertContains(collector, "ZZ")
        assertContains(collector, "ZZZ")
      }

      edtWriteAction {
        wm.updateProjectModel { it addEntity NamedEntity("C", MySource) }
        wm.updateProjectModel { it addEntity NamedEntity("CC", MySource) }
        wm.updateProjectModel { it addEntity NamedEntity("CCC", MySource) }
      }
      waitUntilAllAsserted {
        assertEquals(8, collector.size)
        assertContains(collector, "X")
        assertContains(collector, "Y")
        assertContains(collector, "Z")
        assertContains(collector, "ZZ")
        assertContains(collector, "ZZZ")
        assertContains(collector, "C")
        assertContains(collector, "CC")
        assertContains(collector, "CCC")
      }

      edtWriteAction {
        wm.updateProjectModel { it addEntity SampleEntity2("data", true, MySource) }
      }
      waitUntilAllAsserted {
        assertEquals(8, collector.size)
      }

      edtWriteAction {
        wm.updateProjectModel { it addEntity NamedEntity("A", MySource) }
      }

      waitUntilAllAsserted {
        assertEquals(9, collector.size)
        assertContains(collector, "X")
        assertContains(collector, "Y")
        assertContains(collector, "Z")
        assertContains(collector, "ZZ")
        assertContains(collector, "ZZZ")
        assertContains(collector, "C")
        assertContains(collector, "CC")
        assertContains(collector, "CCC")
        assertContains(collector, "A")
      }

      job.cancelAndJoin()
    }
  }

  @Test
  fun subchildren() = runBlocking {
    val collector1 = ArrayList<String>()
    val collector2 = ArrayList<String>()
    val collector3 = ArrayList<String>()
    val collector4 = ArrayList<String>()
    var counter1 = 0
    var counter2 = 0
    var counter3 = 0
    var counter4 = 0
    edtWriteAction {
      wm.updateProjectModel {
        it addEntity ParentSubEntity("ParentData", MySource) {
          child = ChildSubEntity(MySource) {
            child = ChildSubSubEntity("ChildData", MySource)
          }
        }
      }
    }

    val rete = WmReactive(wm)

    // These two queries will generate the same result, but the events when they
    //   react are very different. In the second case, we'll react to changes of child in ChildSubEntity
    //   however, in the first case we won't.
    val subSubQuery = entities<ChildSubSubEntity>().map { it.childData }
    val subChildQuery = entities<ParentSubEntity>()
      .map { it.child }
      .filterNotNull()
      .map { it.child }
      .filterNotNull()
      .map { it.childData }

    val job1 = launch {
      rete.flowOfQuery(subChildQuery).collect {
        counter1 += 1
        collector1.set(it)
      }
    }

    val job2 = launch {
      rete.flowOfQuery(subSubQuery).collect {
        counter2 += 1
        collector2.set(it)
      }
    }

    val job3 = launch {
      rete.flowOfNewElements(subChildQuery).collect {
        counter3 += 1
        collector3.add(it)
      }
    }

    val job4 = launch {
      rete.flowOfNewElements(subSubQuery).collect {
        counter4 += 1
        collector4.add(it)
      }
    }

    waitUntilAllAsserted {
      assertEquals(1, counter1)
      assertEquals(1, counter2)
      assertEquals(1, counter3)
      assertEquals(1, counter4)
      assertEquals(1, collector1.size)
      assertEquals(1, collector2.size)
      assertEquals(1, collector3.size)
      assertEquals(1, collector4.size)
      assertContains(collector1, "ChildData")
      assertContains(collector2, "ChildData")
      assertContains(collector3, "ChildData")
      assertContains(collector4, "ChildData")
    }

    edtWriteAction {
      wm.updateProjectModel {
        val entity = it.entities(ChildSubEntity::class.java).single()
        it addEntity ParentSubEntity("ParentData2", MySource) parent@{
          it.modifyChildSubEntity(entity) entity@{
            this@parent.child = this@entity
          }
        }
      }
    }

    waitUntilAllAsserted {
      assertEquals(2, counter1)
      assertEquals(1, counter2)
      assertEquals(2, counter3)
      assertEquals(1, counter4)
      assertEquals(1, collector1.size)
      assertEquals(1, collector2.size)
      assertEquals(2, collector3.size)
      assertEquals(1, collector3.toSet().size)
      assertEquals(1, collector4.size)
      assertContains(collector1, "ChildData")
      assertContains(collector2, "ChildData")
      assertContains(collector3, "ChildData")
      assertContains(collector4, "ChildData")
    }

    job1.cancelAndJoin()
    job2.cancelAndJoin()
    job3.cancelAndJoin()
    job4.cancelAndJoin()
  }

  @Test
  fun `delay in read`() = runBlocking {
    val collector = ArrayList<String>()
    edtWriteAction {
      wm.updateProjectModel { it addEntity NamedEntity("X", MySource) }
    }
    val rete = WmReactive(wm)
    val query = entities<NamedEntity>().map { it.myName }

    val job = launch {
      rete.flowOfNewElements(query).collect {
        delay(1.seconds)
        collector.add(it)
      }
    }

    waitUntilAllAsserted {
      assertEquals(1, collector.size)
      assertContains(collector, "X")
    }

    edtWriteAction {
      wm.updateProjectModel { it addEntity NamedEntity("Y", MySource) }
    }
    waitUntilAllAsserted {
      assertEquals(2, collector.size)
      assertContains(collector, "X")
      assertContains(collector, "Y")
    }

    edtWriteAction {
      wm.updateProjectModel { it addEntity NamedEntity("Z", MySource) }
    }
    waitUntilAllAsserted {
      assertEquals(3, collector.size)
      assertContains(collector, "X")
      assertContains(collector, "Y")
      assertContains(collector, "Z")
    }

    job.cancelAndJoin()
  }

  @Test
  fun `flow of entities add and remove`() = runBlocking {
    val collector = ArrayList<String>()
    val removedCollector = ArrayList<String>()
    edtWriteAction {
      wm.updateProjectModel { it addEntity NamedEntity("X", MySource) }
    }
    val rete = WmReactive(wm)
    val query = entities<NamedEntity>()

    val job = launch {
      rete.flowOfDiff(query).collect { events ->
        events.added.forEach {
          collector.add(it.myName)
        }
        events.removed.forEach {
          removedCollector.add(it.myName)
        }
      }
    }

    waitUntilAllAsserted {
      assertEquals(0, removedCollector.size)
      assertEquals(1, collector.size)
      assertContains(collector, "X")
    }

    edtWriteAction {
      wm.updateProjectModel { it addEntity NamedEntity("Y", MySource) }
    }
    waitUntilAllAsserted {
      assertEquals(0, removedCollector.size)
      assertEquals(2, collector.size)
      assertContains(collector, "X")
      assertContains(collector, "Y")
    }

    edtWriteAction {
      wm.updateProjectModel { it.resolve(NameId("X"))!!.also { entity -> it.removeEntity(entity) } }
    }
    waitUntilAllAsserted {
      assertEquals(1, removedCollector.size)
      assertContains(removedCollector, "X")
      assertEquals(2, collector.size)
      assertContains(collector, "X")
      assertContains(collector, "Y")
    }


    // Query for entities doesn't react on changes of entities. Only on adding and remove
    edtWriteAction {
      wm.updateProjectModel {
        it.resolve(NameId("Y"))!!.also { entity ->
          it.modifyNamedEntity(entity) {
            this.myName = "Z"
          }
        }
      }
    }
    edtWriteAction {
      wm.updateProjectModel { it addEntity NamedEntity("C", MySource) }
    }

    waitUntilAllAsserted {
      assertEquals(1, removedCollector.size)
      assertContains(removedCollector, "X")
      assertEquals(3, collector.size)
      assertContains(collector, "X")
      assertContains(collector, "Y")
      assertContains(collector, "C")
    }

    job.cancelAndJoin()
  }

  private suspend fun waitUntilAllAsserted(message: String? = null,
                                           timeout: Duration = DEFAULT_TEST_TIMEOUT,
                                           condition: suspend CoroutineScope.() -> Unit) {
    try {
      waitUntil(message, timeout) {
        var res: Boolean
        try {
          condition()
          res = true
        }
        catch (e: AssertionError) {
          res = false
        }
        res
      }
    }
    catch (e: AssertionError) {
      e.printStackTrace()
      coroutineScope {
        condition()
      }
    }
  }
}