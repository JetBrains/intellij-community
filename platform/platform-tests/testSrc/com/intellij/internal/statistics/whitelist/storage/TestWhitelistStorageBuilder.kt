// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist.storage

import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence
import com.intellij.internal.statistic.eventLog.whitelist.EventLogMetadataLoader
import com.intellij.internal.statistic.eventLog.whitelist.WhitelistStorage

class TestWhitelistStorageBuilder(private val recorderId: String = "TEST") {
  private var cachedContent: String? = ""
  private var serverContentProvider: () -> String = {""}
  private var cachedLastModified: Long = 0
  private var serverLastModified: Long = 0

  fun withCachedContent(content: String): TestWhitelistStorageBuilder {
    cachedContent = content
    return this
  }

  fun withServerContent(content: String): TestWhitelistStorageBuilder {
    serverContentProvider = {content}
    return this
  }

  fun withServerContentProvider(provider: () -> String): TestWhitelistStorageBuilder {
    serverContentProvider = provider
    return this
  }

  fun withCachedLastModified(lastModified: Long): TestWhitelistStorageBuilder {
    cachedLastModified = lastModified
    return this
  }

  fun withServerLastModified(lastModified: Long): TestWhitelistStorageBuilder {
    serverLastModified = lastModified
    return this
  }

  fun build(): TestWhitelistStorage {
    val persistence = TestEventLogWhitelistPersistence(recorderId, cachedContent, cachedLastModified)
    val loader = TestEventLogWhitelistLoader(serverContentProvider, serverLastModified)
    return TestWhitelistStorage(recorderId, persistence, loader)
  }
}

class TestWhitelistStorage(
  recorderId: String, persistence: EventLogWhitelistPersistence, loader: EventLogMetadataLoader
) : WhitelistStorage(recorderId, persistence, loader) {
  fun getGroups(): Set<String> {
    return HashSet<String>(eventsValidators.keys)
  }
}

private class TestEventLogWhitelistPersistence(recorderId: String, private var content: String?, private var modified: Long) : EventLogWhitelistPersistence(recorderId) {
  override fun getCachedMetadata(): String? = content

  override fun cacheWhiteList(gsonWhiteListContent: String, lastModified: Long) {
    content = gsonWhiteListContent
    modified = lastModified
  }

  override fun getLastModified(): Long = modified
}

private class TestEventLogWhitelistLoader(private val provider: () -> String, private val lastModified: Long) : EventLogMetadataLoader {
  override fun loadMetadataFromServer(): String = provider.invoke()

  override fun getLastModifiedOnServer(): Long = lastModified
}