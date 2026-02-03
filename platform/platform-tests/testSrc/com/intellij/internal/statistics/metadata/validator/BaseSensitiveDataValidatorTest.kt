// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.metadata.validator

import com.intellij.internal.statistic.eventLog.EventLogBuild
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.IGroupValidators
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule
import com.intellij.internal.statistic.eventLog.validator.rules.utils.CustomRuleProducer
import com.intellij.internal.statistic.eventLog.validator.storage.FusComponentProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.jetbrains.fus.reporting.FileHandle
import com.jetbrains.fus.reporting.FileStorage
import com.jetbrains.fus.reporting.FileStorageMode
import com.jetbrains.fus.reporting.FusClientConfig
import com.jetbrains.fus.reporting.FusHttpClient
import com.jetbrains.fus.reporting.HttpResponse
import com.jetbrains.fus.reporting.MessageBus
import com.jetbrains.fus.reporting.RegionCode
import com.jetbrains.fus.reporting.RemoteConfig
import com.jetbrains.fus.reporting.defaults.DefaultMetadataStorage
import com.jetbrains.fus.reporting.defaults.NoOpLoggerFactory
import java.io.File
import java.nio.file.Files
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

  private fun singleFileFileStorage(content: String): FileStorage = object : FileStorage {
    override fun exists(path: String): Boolean = true
    override fun openFileHandle(path: String, mode: FileStorageMode): FileHandle = object : FileHandle {
      override fun exists(): Boolean = true
      override fun length(): Long = content.length.toLong()
      override fun read(index: Int): Byte = content[index].code.toByte()
      override fun read(index: Int, dstBuffer: ByteArray): ByteArray = content.substring(index, index + dstBuffer.size)
        .toByteArray()
        .copyInto(dstBuffer)
      override fun readAll(): ByteArray = content.toByteArray()
      override fun write(bytes: ByteArray) = throw NotImplementedError()
      override fun close() = Unit
    }
    override fun read(path: String): ByteArray = content.toByteArray()
    override fun write(path: String, content: ByteArray) = throw NotImplementedError()
  }

  private fun multiFileFileStorage(vararg files: Pair<String, String>): FileStorage = object : FileStorage {
    private val fileHandles = files.map {
      listOf(
        it.first to singleFileFileStorage(it.second),
        "${it.first}.meta" to singleFileFileStorage("1753265087549")
      )
    }.flatten()
    .toMap()
    override fun exists(path: String): Boolean = if (path.isEmpty()) true else fileHandles.containsKey(path)

    override fun openFileHandle(path: String, mode: FileStorageMode): FileHandle = fileHandles[path]!!.openFileHandle(path, mode)

    override fun read(path: String): ByteArray = fileHandles[path]?.read(path)!!

    override fun write(path: String, content: ByteArray) = throw NotImplementedError()
  }

  internal fun createSensitiveDataValidator(fusFileStorage: FileStorage, customBuild: String? = null): TestSensitiveDataValidator {
    val fusComponents = createFusComponents(fusFileStorage)

    return TestSensitiveDataValidator(
      fusComponents,
      RECORDER_ID,
      customBuild
    )
  }

  internal fun createFusComponents(fusFileStorage: FileStorage): FusComponentProvider.FusComponents {
    val fusHttpClient = object : FusHttpClient {
      override fun get(url: String): HttpResponse = HttpResponse(200, "")
      override fun lastModified(url: String): Long = 0L
      override fun post(url: String, data: String): HttpResponse = HttpResponse(200, "")
    }

    val messageBus = MessageBus()

    val remoteConfig = object : RemoteConfig {
      override fun getDictionaryUrl(): String = ""
      override fun getMetadataUrl(): String = ""
      override fun getSendUrl(): String = ""
      override fun provideOptions(): Map<String, String> = emptyMap()
    }

    val fusConfig = FusClientConfig(
      "TEST",
      "TEST",
      RECORDER_ID,
      RegionCode.ALL,
      "2025.2",
      252,
      null,
      true,
      true
    )

    val jsonSerializer = FusComponentProvider.FusJacksonSerializer()

    val metadataStorage = DefaultMetadataStorage(
      fusConfig,
      messageBus,
      NoOpLoggerFactory(),
      remoteConfig,
      fusHttpClient,
      jsonSerializer,
      fusFileStorage,
      null,
      buildParser = { build -> EventLogBuild.fromString(build) },
      utilRulesProducer = CustomRuleProducer(RECORDER_ID),
      excludedFields = FeatureUsageData.platformDataKeys
    )

    val fusComponents = FusComponentProvider.FusComponents(metadataStorage, messageBus, remoteConfig)
    return fusComponents
  }

  internal fun newValidator(content: String, customBuild: String? = null): TestSensitiveDataValidator {
    val fusFileStorage = multiFileFileStorage("events-scheme.json" to content)
    return createSensitiveDataValidator(fusFileStorage, customBuild)
  }

  internal fun newValidatorByFile(fileName: String): TestSensitiveDataValidator {
    return newValidator(loadContent(fileName))
  }

  internal fun newValidatorByFileWithDictionary(fileName: String, dictionaryFileName: String): TestSensitiveDataValidator {
    val content = loadContent(fileName)
    val dictionaryFile = File(PlatformTestUtil.getPlatformTestDataPath() + "fus/validation/" + dictionaryFileName)
    val fusFileStorage = multiFileFileStorage(
      "events-scheme.json" to content,
      "dictionaries/${dictionaryFile.name}" to Files.readString(dictionaryFile.toPath())
    )
    return createSensitiveDataValidator(fusFileStorage)
  }

  internal fun loadContent(fileName: String): String {
    val file = File(PlatformTestUtil.getPlatformTestDataPath() + "fus/validation/" + fileName)
    assertTrue { file.exists() }
    return FileUtil.loadFile(file)
  }
}

internal class TestSensitiveDataValidator(storage: FusComponentProvider.FusComponents, recorderId: String, private val customBuild: String? = null) : IntellijSensitiveDataValidator(storage, recorderId) {
  private fun filterGroupValidatorsByCustomBuild(group: EventLogGroup, groupValidators: IGroupValidators<EventLogBuild>): IGroupValidators<EventLogBuild>? {
    if (customBuild == null) return groupValidators

    if (groupValidators.versionFilter?.accepts(group.id, group.version.toString(), customBuild) ?: true) {
      return groupValidators
    }

    return null
  }

  fun getEventRules(group: EventLogGroup): Array<FUSRule> {
    val groupValidators = filterGroupValidatorsByCustomBuild(group, validationRulesStorage.getGroupValidators(group.id))
    val rules = groupValidators?.eventGroupRules
    return rules?.getEventIdRules() ?: FUSRule.EMPTY_ARRAY
  }

  fun getEventDataRules(group: EventLogGroup): Map<String, Array<FUSRule>> {
    val groupValidators = filterGroupValidatorsByCustomBuild(group, validationRulesStorage.getGroupValidators(group.id))
    val rules = groupValidators?.eventGroupRules
    return rules?.getEventDataRules() ?: emptyMap()
  }

  fun validateEvent(context: EventContext, groupId: String): ValidationResultType {
    return validateEvent(context, validationRulesStorage.getGroupValidators(groupId).eventGroupRules)
  }

  fun guaranteeCorrectEventData(groupId: String, context: EventContext): Map<String, Any> {
    return guaranteeCorrectEventData(context, validationRulesStorage.getGroupValidators(groupId).eventGroupRules)
  }

}