// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist.validator

import com.intellij.internal.statistic.eventLog.EventLogBuild
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules
import com.intellij.internal.statistic.eventLog.whitelist.EventLogWhitelistLoader
import com.intellij.internal.statistic.eventLog.whitelist.WhitelistStorage
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import java.io.File
import kotlin.test.assertTrue

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
    val storage = object : WhitelistStorage("TEST", TestEventLogWhitelistPersistence(content), TestEventLogWhitelistLoader(content)) {
      override fun createValidators(build: EventLogBuild?,
                                    groups: FUStatisticsWhiteListGroupsService.WLGroups): MutableMap<String, WhiteListGroupRules> {
        if (customBuild != null) {
          return super.createValidators(EventLogBuild.fromString(customBuild), groups)
        }
        return super.createValidators(build, groups)
      }
    }
    return TestSensitiveDataValidator(storage)
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

internal class TestSensitiveDataValidator(storage: WhitelistStorage) : SensitiveDataValidator(storage) {
  fun getEventRules(group: EventLogGroup): Array<FUSRule> {
    val whiteListRule = myWhiteListStorage.getGroupRules(group.id)

    return if (whiteListRule == null) FUSRule.EMPTY_ARRAY else whiteListRule.eventIdRules
  }

  fun getEventDataRules(group: EventLogGroup): Map<String, Array<FUSRule>> {
    val whiteListRule = myWhiteListStorage.getGroupRules(group.id)

    return if (whiteListRule == null) emptyMap() else whiteListRule.eventDataRules
  }
}

class TestEventLogWhitelistPersistence(private val myContent: String) : EventLogWhitelistPersistence("TEST") {
  override fun getCachedMetadata(): String? {
    return myContent
  }
}

class TestEventLogWhitelistLoader(private val myContent: String) : EventLogWhitelistLoader {
  override fun getLastModifiedOnServer(): Long = 0

  override fun loadWhiteListFromServer(): String = myContent
}