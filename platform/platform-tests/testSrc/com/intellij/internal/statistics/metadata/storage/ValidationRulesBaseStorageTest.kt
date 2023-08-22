// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.metadata.storage

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationTestRulesPersistedStorage
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogTestMetadataPersistence
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.IOException

internal abstract class ValidationRulesBaseStorageTest : BasePlatformTestCase() {
  protected val groupId = "test.group"
  protected val recorderId = "TEST"
  protected val secondRecorderId = "SECOND_TEST"

  private val recordersToCleanUp = listOf(recorderId, secondRecorderId)

  override fun setUp() {
    super.setUp()

    // initialize validation rules storage
    for (recorder in recordersToCleanUp) {
      IntellijSensitiveDataValidator.getInstance(recorder)
    }
  }

  override fun tearDown() {
    try {
      ValidationTestRulesPersistedStorage.cleanupAll()
      for (recorder in recordersToCleanUp) {
        val file = EventLogTestMetadataPersistence(recorder).eventsTestSchemeFile
        try {
          FileUtil.delete(file.parent)
        }
        catch (e: IOException) {
          LOG.error(e)
        }
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }
}