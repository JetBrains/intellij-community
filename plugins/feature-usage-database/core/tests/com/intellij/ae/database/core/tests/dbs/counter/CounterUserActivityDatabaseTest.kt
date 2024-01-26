// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.core.tests.dbs.counter

import com.intellij.ae.database.core.activities.DatabaseBackedCounterUserActivity
import com.intellij.ae.database.core.dbs.counter.CounterUserActivityDatabase
import com.intellij.ae.database.core.tests.dbs.runDatabaseLayerTest
import com.intellij.ae.database.core.utils.InstantUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert

class CounterUserActivityDatabaseTest : BasePlatformTestCase() {
  private val databaseFactory = { cs: CoroutineScope ->
    CounterUserActivityDatabase(cs)
  }

  fun testGetActivitySum() = runDatabaseLayerTest(databaseFactory) { it ->
    val item1 = 13
    val item2 = 1989
    val result = item1+item2

    val activity = createActivity()
    it.submitDirect(activity, item1, InstantUtils.Now, null)
    it.submitDirect(activity, item2, InstantUtils.Now, null)

    val fromDb = it.getActivitySum(activity, InstantUtils.SomeTimeAgo, InstantUtils.NowButABitLater)

    Assert.assertEquals(result, fromDb)
  }

  private fun createActivity() = object : DatabaseBackedCounterUserActivity() {
    override val id = "testActivity"
  }
}