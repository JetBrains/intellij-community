// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.metadata.validator

import com.intellij.internal.statistic.eventLog.EventLogBuild
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules
import com.intellij.internal.statistic.eventLog.validator.storage.EventLogMetadataLoader
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationRulesPersistedStorage
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataPersistence
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import java.io.File
import kotlin.test.assertTrue

private const val RECORDER_ID = "TEST"

abstract class BaseSensitiveDataValidatorTest  : UsefulTestCase() {
  private var myFixture: CodeInsightTestFixture? = null

  override fun setUp() {
    super.setUp()

    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val fixtureBuilder = factory.createFixtureBuilder("SensitiveDataValidatorTest")
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixtureBuilder.fixture)
    myFixture?.setUp()
  }

  override fun tearDown() {
    try {
      myFixture?.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  internal fun newValidator(content: String, customBuild: String? = null): TestSensitiveDataValidator {
    val storage = object : ValidationRulesPersistedStorage(RECORDER_ID, TestEventLogMetadataPersistence(content), TestEventLogMetadataLoader(content)) {
      override fun createValidators(build: EventLogBuild?,
                                    groups: EventGroupRemoteDescriptors): MutableMap<String, EventGroupRules> {
        if (customBuild != null) {
          return super.createValidators(EventLogBuild.fromString(customBuild), groups)
        }
        return super.createValidators(build, groups)
      }
    }
    return TestSensitiveDataValidator(storage, RECORDER_ID)
  }

  internal fun newValidatorByFile(fileName: String): TestSensitiveDataValidator {
    return newValidator(loadContent(fileName))
  }

  internal fun loadContent(fileName: String): String {
    val file = File(PlatformTestUtil.getPlatformTestDataPath() + "fus/validation/" + fileName)
    assertTrue { file.exists() }
    return FileUtil.loadFile(file)
  }
}

internal class TestSensitiveDataValidator(storage: ValidationRulesPersistedStorage, recorderId: String) : IntellijSensitiveDataValidator(storage, recorderId) {
  fun getEventRules(group: EventLogGroup): Array<FUSRule> {
    val rules = validationRulesStorage.getGroupRules(group.id)

    return if (rules == null) FUSRule.EMPTY_ARRAY else rules.eventIdRules
  }

  fun getEventDataRules(group: EventLogGroup): Map<String, Array<FUSRule>> {
    val rules = validationRulesStorage.getGroupRules(group.id)

    return if (rules == null) emptyMap() else rules.eventDataRules
  }

  fun validateEvent(context: EventContext, groupId: String): ValidationResultType {
    return validateEvent(context, validationRulesStorage.getGroupRules(groupId))
  }

  fun guaranteeCorrectEventData(groupId: String, context: EventContext): Map<String, Any> {
    return guaranteeCorrectEventData(context, validationRulesStorage.getGroupRules(groupId))
  }

}

class TestEventLogMetadataPersistence(private val myContent: String) : EventLogMetadataPersistence("TEST") {
  override fun getCachedEventsScheme(): String {
    return myContent
  }
}

class TestEventLogMetadataLoader(private val myContent: String) : EventLogMetadataLoader {
  override fun getLastModifiedOnServer(): Long = 0

  override fun loadMetadataFromServer(): String = myContent

  override fun getOptionValue(name: String): String? = null
}