// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.cache

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.platform.workspace.storage.query.map
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity2
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.fail

class CacheApiConcurrencyTest {
  @Test
  fun `concurrency test create new snapshot with parallel read`() {
    val builder = MutableEntityStorage.create()

    builder addEntity SampleEntity2("info", false, MySource)

    val snapshot = builder.toSnapshot()

    val query = entities<SampleEntity2>().map { it.data }
    val res = snapshot.cached(query)
    assertEquals("info", res.single())

    repeat(10_000) {
      val builder2 = snapshot.toBuilder()
      repeat(1000) {
        builder2 addEntity SampleEntity2("info$it", false, MySource)
      }

      val snapshot2 = builder2.toSnapshot()

      var exceptionOne: Throwable? = null
      var exceptionTwo: Throwable? = null
      val threadOne = thread {
        exceptionOne = runCatching {
          val res2 = snapshot2.cached(query)
          assertEquals(1001, res2.size)
        }.exceptionOrNull()
      }
      val threadTwo = thread {
        exceptionTwo = runCatching {
          val snapshot3 = snapshot2.toBuilder().toSnapshot()
          assertEquals(1001, snapshot3.cached(query).size)
        }.exceptionOrNull()
      }

      threadOne.join()
      threadTwo.join()
      exceptionOne?.let { fail("Exception in first thread", it) }
      exceptionTwo?.let { fail("Exception in second thread", it) }
    }
  }
}
