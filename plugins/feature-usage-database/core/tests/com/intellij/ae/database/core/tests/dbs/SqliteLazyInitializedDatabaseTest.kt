// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.core.tests.dbs

import com.intellij.ae.database.core.IdService
import com.intellij.ae.database.core.dbs.SqliteLazyInitializedDatabase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.sqlite.EmptyBinder
import org.jetbrains.sqlite.ObjectBinderFactory
import org.junit.Assert

class SqliteLazyInitializedDatabaseTest : BasePlatformTestCase() {
  /**
   * Read/write inside once sql connection
   */
  fun testBasicReadWrite(): Unit = timeoutRunBlocking {
    System.setProperty("ae.database.path", FileUtil.generateRandomTemporaryPath("ae", ".db").absolutePath)
    val db1 = SqliteLazyInitializedDatabase(this)
    withContext(Dispatchers.Default) {
      db1.execute { initDb ->
        val writeStatement = initDb.prepareStatement("insert into ide(ide_id, machine_id, family) values (?, ?, ?)",
                                                     ObjectBinderFactory.create3<String, String, String>()).apply {
          binder.bind("test1", "test2", "test3")
        }
        writeStatement.executeUpdate()

        val readStatement = initDb.prepareStatement("SELECT * FROM ide WHERE ide_id IS (?)", ObjectBinderFactory.create1<String>()).apply {
          binder.bind("test1")
        }
        val resultSet = readStatement.executeQuery()
        var hits = 0
        while (resultSet.next()) {
          ++hits
          // column zero is database id, not important here
          val machineId = resultSet.getString(1)
          val ideId = resultSet.getString(2)
          val family = resultSet.getString(3)
          Assert.assertArrayEquals(arrayOf("test1", "test2", "test3"), arrayOf(ideId, machineId, family))
        }
        Assert.assertEquals(1, hits)
      }
      db1.closeDatabase()
    }
  }

  /**
   * Write to DB, close it, open, read
   */
  fun testReadWriteAfterClosing(): Unit = timeoutRunBlocking {
    System.setProperty("ae.database.path", FileUtil.generateRandomTemporaryPath("ae", ".db").absolutePath)

    withContext(Dispatchers.Default) {
      val db1 = SqliteLazyInitializedDatabase(this)
      db1.execute { initDb ->
        val writeStatement = initDb.prepareStatement("insert into ide(ide_id, machine_id, family) values (?, ?, ?)",
                                                     ObjectBinderFactory.create3<String, String, String>()).apply {
          binder.bind("test1", "test2", "test3")
        }
        writeStatement.executeUpdate()
      }
      db1.closeDatabase()

      val db2 = SqliteLazyInitializedDatabase(this)
      db2.execute { initDb ->
        val readStatement = initDb.prepareStatement("SELECT * FROM ide WHERE ide_id IS (?)", ObjectBinderFactory.create1<String>()).apply {
          binder.bind("test1")
        }
        val resultSet = readStatement.executeQuery()
        var hits = 0
        while (resultSet.next()) {
          ++hits
          // column zero is database id, not important here
          val machineId = resultSet.getString(1)
          val ideId = resultSet.getString(2)
          val family = resultSet.getString(3)
          Assert.assertArrayEquals(arrayOf("test1", "test2", "test3"), arrayOf(ideId, machineId, family))
        }
        Assert.assertEquals(1, hits)
      }
      db2.closeDatabase()
    }
  }

  fun testProperMetadata(): Unit = timeoutRunBlocking {
    System.setProperty("ae.database.path", FileUtil.generateRandomTemporaryPath("ae", ".db").absolutePath)

    withContext(Dispatchers.Default) {
      val db1 = SqliteLazyInitializedDatabase(this)
      db1.execute { initDb, metadata ->
        Assert.assertEquals(1, metadata.ideId)

        val entries = initDb.prepareStatement("SELECT * FROM ide", EmptyBinder).executeQuery()
        var hits = 0
        while (entries.next()) {
          ++hits
          // column zero is database id, not important here
          val machineId = entries.getString(1)
          val ideId = entries.getString(2)
          val family = entries.getString(3)
          Assert.assertArrayEquals(arrayOf(IdService.getInstance().id, IdService.getInstance().machineId, IdService.getInstance().ideCode), arrayOf(ideId, machineId, family))
        }
        Assert.assertEquals(1, hits)
      }
      db1.closeDatabase()
    }
  }
}