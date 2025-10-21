// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.metadata.storage

import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException.EventLogMetadataLoadErrorType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import java.io.File
import java.nio.file.Files

class ValidationRulesStorageUpdateTest : UsefulTestCase() {
  private var myFixture: CodeInsightTestFixture? = null

  override fun setUp() {
    super.setUp()

    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val fixtureBuilder = factory.createFixtureBuilder("ValidationRulesStorageUpdateTest")
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

  private fun getTestDataRoot() = PlatformTestUtil.getPlatformTestDataPath() + "fus/metadata/storage"

  private fun getDictionaryTestDataRoot() = PlatformTestUtil.getPlatformTestDataPath() + "fus/metadata/dictionaries"

  private fun getTestDataFileOrDefault(withDefaultFiles: Boolean, extension: String): File {
    return if (withDefaultFiles) getDefaultTestDataFile(extension) else getTestDataFile(getTestDataRoot(), extension)
  }

  private fun getDictionaryTestDataFile(extension: String): File {
    return getTestDataFile(getDictionaryTestDataRoot(), extension)
  }

  private fun getTestDataFile(testDataRoot: String, extension: String): File {
    val testName = getTestName(false).trimStart('_')
    return File("$testDataRoot/$testName.$extension")
  }

  private fun getDefaultTestDataFile(extension: String): File {
    return File(getTestDataRoot() + "/default_rules_storage_test." + extension)
  }

  private fun newBuilder(withDefaultFiles: Boolean = true): TestValidationRulesStorageBuilder {
    val builder = TestValidationRulesStorageBuilder()
    val cached = getTestDataFileOrDefault(withDefaultFiles, "cached.json")
    if (cached.exists()) {
      builder.withCachedContent(FileUtil.loadFile(cached))
    }

    val server = getTestDataFileOrDefault(withDefaultFiles, "server.json")
    if (server.exists()) {
      builder.withServerContent(FileUtil.loadFile(server))
    }

    val cachedDictionaryFile = getDictionaryTestDataFile("cached.ndjson")
    if (cachedDictionaryFile.exists()) {
      builder.withCachedDictionary(cachedDictionaryFile)
    }

    val serverDictionaryFile = getDictionaryTestDataFile("server.ndjson")
    if (serverDictionaryFile.exists()) {
      builder.withServerDictionary(Files.readString(serverDictionaryFile.toPath()))
    }
    return builder
  }

  fun doTest(storage: TestValidationRulesStorage, vararg expectedGroups: String) {
    storage.update()
    assertEquals(hashSetOf(*expectedGroups), storage.getGroups())
  }

  fun test_latest_cached_rules() {
    val storage = newBuilder().
      withCachedLastModified(1583852308336).
      withServerLastModified(1583852308336).build()
    doTest(storage, "cached.test.group")
  }

  fun test_cached_rules_older_than_server() {
    val storage = newBuilder().
      withCachedLastModified(1583852318336).
      withServerLastModified(1583852308336).build()
    doTest(storage, "cached.test.group")
  }

  fun test_server_rules_older_than_cached() {
    val storage = newBuilder().
      withCachedLastModified(1583852318336).
      withServerLastModified(1583852328336).build()
    doTest(storage, "server.test.group")
  }

  fun test_no_cached_rules() {
    val storage = newBuilder(false).
      withServerLastModified(1583852308336).build()
    doTest(storage, "server.test.group")
  }

  fun test_invalid_cached_rules() {
    val storage = newBuilder(false).
      withCachedLastModified(1583852308336).
      withServerLastModified(1583852318336).build()
    doTest(storage, "server.test.group")
  }

  fun test_latest_but_invalid_cached_rules() {
    val storage = newBuilder(false).
      withCachedLastModified(1583852328336).
      withServerLastModified(1583852308336).build()
    doTest(storage, "server.test.group")
  }

  fun test_failed_loading_last_modified_from_server() {
    val storage = newBuilder().
      withCachedLastModified(1583852308336).build()
    doTest(storage, "server.test.group")
  }

  fun test_no_last_modified() {
    val storage = newBuilder().build()
    doTest(storage, "server.test.group")
  }

  fun test_failed_loading_server_rules() {
    val storage = newBuilder().
      withCachedLastModified(1583852308336).
      withServerLastModified(1583852318336).
      withServerContentProvider { throw EventLogMetadataLoadException(
        EventLogMetadataLoadErrorType.ERROR_ON_LOAD)
      }.
      build()
    doTest(storage, "cached.test.group")
  }

  fun test_failed_parsing_server_rules() {
    val storage = newBuilder(false).
      withCachedLastModified(1583852308336).
      withServerLastModified(1583852318336).
      build()
    doTest(storage, "cached.test.group")
  }

  fun test_cached_dictionary_is_newer_than_server() {
    val storage = newBuilder(false).
      withCachedLastModified(1583852308336).
      withServerLastModified(1).
      build()
    doTest(storage)
    val dictionary = storage.dictionaryStorage?.getDictionaryByName("cached_dictionary_is_newer_than_server.cached.ndjson")
    assertTrue(dictionary?.contains("value5") ?: false)
  }

  fun test_cached_dictionary_is_older_than_server() {
    val storage = newBuilder(false).
      withCachedLastModified(1).
      withServerLastModified(1583852308336).
      build()
    doTest(storage)
    val dictionary = storage.dictionaryStorage?.getDictionaryByName("cached_dictionary_is_older_than_server.cached.ndjson")
    assertFalse(dictionary?.contains("value5") ?: true)
  }
}