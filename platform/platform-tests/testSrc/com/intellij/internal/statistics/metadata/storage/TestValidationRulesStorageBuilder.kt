// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.metadata.storage

import com.intellij.internal.statistic.eventLog.validator.storage.EventLogMetadataLoader
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationRulesPersistedStorage
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataPersistence

class TestValidationRulesStorageBuilder(private val recorderId: String = "TEST") {
  private var cachedContent: String? = ""
  private var serverContentProvider: () -> String = {""}
  private var cachedLastModified: Long = 0
  private var serverLastModified: Long = 0

  fun withCachedContent(content: String): TestValidationRulesStorageBuilder {
    cachedContent = content
    return this
  }

  fun withServerContent(content: String): TestValidationRulesStorageBuilder {
    serverContentProvider = {content}
    return this
  }

  fun withServerContentProvider(provider: () -> String): TestValidationRulesStorageBuilder {
    serverContentProvider = provider
    return this
  }

  fun withCachedLastModified(lastModified: Long): TestValidationRulesStorageBuilder {
    cachedLastModified = lastModified
    return this
  }

  fun withServerLastModified(lastModified: Long): TestValidationRulesStorageBuilder {
    serverLastModified = lastModified
    return this
  }

  fun build(): TestValidationRulesStorage {
    val persistence = TestEventLogMetadataPersistence(recorderId, cachedContent, cachedLastModified)
    val loader = TestEventLogMetadataLoader(serverContentProvider, serverLastModified)
    return TestValidationRulesStorage(recorderId, persistence, loader)
  }
}

class TestValidationRulesStorage(
  recorderId: String, persistence: EventLogMetadataPersistence, loader: EventLogMetadataLoader
) : ValidationRulesPersistedStorage(recorderId, persistence, loader) {
  fun getGroups(): Set<String> {
    return HashSet<String>(eventsValidators.keys)
  }
}

private class TestEventLogMetadataPersistence(recorderId: String, private var content: String?, private var modified: Long) : EventLogMetadataPersistence(recorderId) {
  override fun getCachedEventsScheme(): String? = content

  override fun cacheEventsScheme(eventsSchemeJson: String, lastModified: Long) {
    content = eventsSchemeJson
    modified = lastModified
  }

  override fun getLastModified(): Long = modified
}

private class TestEventLogMetadataLoader(private val provider: () -> String, private val lastModified: Long) : EventLogMetadataLoader {
  override fun loadMetadataFromServer(): String = provider.invoke()

  override fun getLastModifiedOnServer(): Long = lastModified

  override fun getOptionValues(): Map<String, String> = emptyMap()
}