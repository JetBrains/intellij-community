// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereFrontendBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val KEY_ATTRIBUTES: CredentialAttributes = CredentialAttributes(generateServiceName("Search Everywhere", "OpenAI API key"))

private const val NOTIFICATION_GROUP_ID: String = "Search Everywhere Actions Ranker"

private val LOG = logger<SeOpenAiKeyStore>()

/**
 * Returns `true` when [key] can go into an HTTP header: it is not empty and has only visible ASCII characters.
 * An OpenAI API key has only such characters.
 */
internal fun isValidOpenAiKey(key: String): Boolean = key.isNotEmpty() && key.all { it in '!'..'~' }

/**
 * Keeps the personal OpenAI API key of the Decision API ranker in [PasswordSafe].
 *
 * The store reads the key once and keeps it in memory. Search Everywhere asks for an endpoint on each query until a session opens.
 * [SeOpenAiDecisionsEndpointProvider] reads the key, and an internal action sets it.
 * When the OpenAI API rejects the key, the store tells the user, and it serves no key until the user sets a key again.
 */
@Service(Service.Level.APP)
internal class SeOpenAiKeyStore internal constructor(private val credentialStore: () -> CredentialStore) {
  @Suppress("unused") // the service container instantiates this one
  constructor() : this({ PasswordSafe.instance })

  private val mutex = Mutex()
  private var isLoaded = false
  private var key: String? = null
  private val rejectedKey = AtomicReference<String?>()
  private val isLimitReported = AtomicBoolean()

  /** Returns the key, or `null` when the user set no key or the OpenAI API rejected it. */
  suspend fun getKey(): String? {
    val key = loadKey() ?: return null
    return key.takeUnless { it == rejectedKey.get() }
  }

  /** Stores [newKey], or removes the stored key when [newKey] is blank. A key that [isValidOpenAiKey] refuses is an error. */
  suspend fun setKey(newKey: String): Unit = mutex.withLock {
    val value = newKey.trim().ifEmpty { null }
    require(value == null || isValidOpenAiKey(value)) { "The OpenAI API key has a character that an HTTP header does not accept" }
    withContext(Dispatchers.IO) { credentialStore().setPassword(KEY_ATTRIBUTES, value) }
    key = value
    isLoaded = true
    rejectedKey.set(null)
  }

  /**
   * Handles a refusal of the OpenAI API for [refusedKey]. See [SeDecisionsEndpointLease.refused].
   *
   * The store serves no rejected key (401 or 403) until the user sets a key again. A rate or a quota limit (429) keeps the key.
   * The user gets one notification for each rejected key, and one for the first limit.
   */
  fun refused(refusedKey: String, statusCode: Int) {
    val bundle = SearchEverywhereFrontendBundle.bundle
    when (statusCode) {
      401, 403 -> if (rejectedKey.getAndSet(refusedKey) != refusedKey) {
        notifyUser(bundle.getMessage("search.everywhere.openai.key.rejected.title"),
                   bundle.getMessage("search.everywhere.openai.key.rejected.content"))
      }
      429 -> if (isLimitReported.compareAndSet(false, true)) {
        notifyUser(bundle.getMessage("search.everywhere.openai.limit.title"),
                   bundle.getMessage("search.everywhere.openai.limit.content"))
      }
    }
  }

  private suspend fun loadKey(): String? = mutex.withLock {
    if (!isLoaded) {
      val stored = withContext(Dispatchers.IO) { credentialStore().getPassword(KEY_ATTRIBUTES) }?.trim()?.ifEmpty { null }
      if (stored != null && !isValidOpenAiKey(stored)) {
        // The report names no part of the key.
        LOG.warn("The stored OpenAI API key of the Search Everywhere ranker has a character that an HTTP header does not accept")
      }
      key = stored?.takeIf(::isValidOpenAiKey)
      isLoaded = true
    }
    key
  }

  private fun notifyUser(title: @NlsContexts.NotificationTitle String, content: @NlsContexts.NotificationContent String) {
    NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(title, content, NotificationType.WARNING)
      .notify(null)
  }
}
