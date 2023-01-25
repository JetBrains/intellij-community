// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite

object SqlTestMain {
  @JvmStatic
  fun main(args: Array<String>) {
    System.setProperty("sqlite.use.path.manager", "false")
    SqliteConnection(file = null).use {
      testInsert(SqliteConnection(file = null))
      println("succeed")
    }
  }
}