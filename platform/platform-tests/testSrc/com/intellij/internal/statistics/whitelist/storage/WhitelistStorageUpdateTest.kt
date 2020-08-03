// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist.storage

import com.intellij.internal.statistic.service.fus.EventLogMetadataLoadException
import com.intellij.internal.statistic.service.fus.EventLogMetadataLoadException.EventLogMetadataLoadErrorType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import java.io.File

class WhitelistStorageUpdateTest : UsefulTestCase() {
  private var myFixture: CodeInsightTestFixture? = null

  override fun setUp() {
    super.setUp()

    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val fixtureBuilder = factory.createFixtureBuilder("WhitelistStorageUpdateTest")
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

  private fun getTestDataRoot() = PlatformTestUtil.getPlatformTestDataPath() + "fus/whitelist/storage"

  private fun getTestDataFileOrDefault(withDefaultFiles: Boolean, extension: String): File {
    return if (withDefaultFiles) getDefaultTestDataFile(extension) else getTestDataFile(extension)
  }

  private fun getTestDataFile(extension: String): File {
    val testName = getTestName(false).trimStart('_')
    return File(getTestDataRoot() + "/" + testName + "." + extension)
  }

  private fun getDefaultTestDataFile(extension: String): File {
    return File(getTestDataRoot() + "/default_whitelist_storage_test." + extension)
  }

  private fun newBuilder(withDefaultFiles: Boolean = true): TestWhitelistStorageBuilder {
    val builder = TestWhitelistStorageBuilder()
    val cached = getTestDataFileOrDefault(withDefaultFiles, "cached.json")
    if (cached.exists()) {
      builder.withCachedContent(FileUtil.loadFile(cached))
    }

    val server = getTestDataFileOrDefault(withDefaultFiles, "server.json")
    if (server.exists()) {
      builder.withServerContent(FileUtil.loadFile(server))
    }
    return builder
  }

  fun doTest(storage: TestWhitelistStorage, vararg expectedGroups: String) {
    storage.update()
    assertEquals(hashSetOf(*expectedGroups), storage.getGroups())
  }

  fun test_latest_cached_whitelist() {
    val storage = newBuilder().
      withCachedLastModified(1583852308336).
      withServerLastModified(1583852308336).build()
    doTest(storage, "cached.test.group")
  }

  fun test_cached_whitelist_older_than_server() {
    val storage = newBuilder().
      withCachedLastModified(1583852318336).
      withServerLastModified(1583852308336).build()
    doTest(storage, "cached.test.group")
  }

  fun test_server_whitelist_older_than_cached() {
    val storage = newBuilder().
      withCachedLastModified(1583852318336).
      withServerLastModified(1583852328336).build()
    doTest(storage, "server.test.group")
  }

  fun test_no_cached_whitelist() {
    val storage = newBuilder(false).
      withServerLastModified(1583852308336).build()
    doTest(storage, "server.test.group")
  }

  fun test_invalid_cached_whitelist() {
    val storage = newBuilder(false).
      withCachedLastModified(1583852308336).
      withServerLastModified(1583852318336).build()
    doTest(storage, "server.test.group")
  }

  fun test_latest_but_invalid_cached_whitelist() {
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

  fun test_failed_loading_server_whitelist() {
    val storage = newBuilder().
      withCachedLastModified(1583852308336).
      withServerLastModified(1583852318336).
      withServerContentProvider { throw EventLogMetadataLoadException(
        EventLogMetadataLoadErrorType.ERROR_ON_LOAD)
      }.
      build()
    doTest(storage, "cached.test.group")
  }

  fun test_failed_parsing_server_whitelist() {
    val storage = newBuilder(false).
      withCachedLastModified(1583852308336).
      withServerLastModified(1583852318336).
      build()
    doTest(storage, "cached.test.group")
  }
}