// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist

import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogTestWhitelistPersistence
import com.intellij.internal.statistic.eventLog.whitelist.WhitelistTestGroupStorage
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.io.IOException

internal abstract class WhitelistBaseStorageTest : BasePlatformTestCase() {
  protected val groupId = "test.group"
  protected val recorderId = "TEST"
  protected val secondRecorderId = "SECOND_TEST"

  private val recordersToCleanUp = listOf(recorderId, secondRecorderId)

  override fun tearDown() {
    super.tearDown()

    WhitelistTestGroupStorage.cleanupAll()
    for (recorder in recordersToCleanUp) {
      val file = EventLogTestWhitelistPersistence(recorder).whitelistFile
      try {
        FileUtil.delete(File(file.parent))
      }
      catch (e: IOException) {
        LOG.error(e)
      }
    }
  }
}