// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereFrontendBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.PropertyKey
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val BUNDLE: String = "messages.searchEverywhereFrontendBundle"

private const val NOTIFICATION_GROUP_ID: String = "Search Everywhere Actions Ranker"

private const val AUTHORIZATION_HEADER: String = "Authorization"

private val LOG = logger<SeApiKeyStore>()

/**
 * Returns `true` when [key] can go into an HTTP header: it is not empty and has only visible ASCII characters.
 * An API key of OpenAI, TypeSafe, or OpenRouter has only such characters.
 */
internal fun isValidApiKey(key: String): Boolean = key.isNotEmpty() && key.all { it in '!'..'~' }

/** One kind of personal API key of a Search Everywhere ranker. It names the credential and the texts that the user sees. */
internal enum class SeApiKeyKind(
  /** Names the key in [PasswordSafe] and in the logs. */
  val keyName: String,
  @PropertyKey(resourceBundle = BUNDLE) val dialogTitleKey: String,
  @PropertyKey(resourceBundle = BUNDLE) val dialogMessageKey: String,
  @PropertyKey(resourceBundle = BUNDLE) val rejectedTitleKey: String,
  @PropertyKey(resourceBundle = BUNDLE) val rejectedContentKey: String,
  @PropertyKey(resourceBundle = BUNDLE) val limitTitleKey: String,
  @PropertyKey(resourceBundle = BUNDLE) val limitContentKey: String,
) {
  OPENAI(
    keyName = "OpenAI API key",
    dialogTitleKey = "search.everywhere.openai.key.dialog.title",
    dialogMessageKey = "search.everywhere.openai.key.dialog.message",
    rejectedTitleKey = "search.everywhere.openai.key.rejected.title",
    rejectedContentKey = "search.everywhere.openai.key.rejected.content",
    limitTitleKey = "search.everywhere.openai.limit.title",
    limitContentKey = "search.everywhere.openai.limit.content",
  ),
  JEV(
    keyName = "Jev API key",
    dialogTitleKey = "search.everywhere.jev.key.dialog.title",
    dialogMessageKey = "search.everywhere.jev.key.dialog.message",
    rejectedTitleKey = "search.everywhere.jev.key.rejected.title",
    rejectedContentKey = "search.everywhere.jev.key.rejected.content",
    limitTitleKey = "search.everywhere.jev.limit.title",
    limitContentKey = "search.everywhere.jev.limit.content",
  );

  val credentialAttributes: CredentialAttributes
    get() = CredentialAttributes(generateServiceName("Search Everywhere", keyName))
}

/**
 * Keeps one personal API key of a Search Everywhere ranker in [PasswordSafe]. [kind] names the key and its texts.
 *
 * The store reads the key once and keeps it in memory. Search Everywhere asks for an endpoint on each query until a session opens.
 * An endpoint provider or a ranker reads the key, and an internal action sets it.
 * When the API rejects the key, the store tells the user, and it serves no key until the user sets a key again.
 */
internal abstract class SeApiKeyStore(val kind: SeApiKeyKind, private val credentialStore: () -> CredentialStore) {
  private val mutex = Mutex()
  private var isLoaded = false
  private var key: String? = null
  private val rejectedKey = AtomicReference<String?>()
  private val isLimitReported = AtomicBoolean()

  /** Returns the key, or `null` when the user set no key or the API rejected it. */
  suspend fun getKey(): String? {
    val key = loadKey() ?: return null
    return key.takeUnless { it == rejectedKey.get() }
  }

  /** Stores [newKey], or removes the stored key when [newKey] is blank. A key that [isValidApiKey] refuses is an error. */
  suspend fun setKey(newKey: String): Unit = mutex.withLock {
    val value = newKey.trim().ifEmpty { null }
    require(value == null || isValidApiKey(value)) { "The ${kind.keyName} has a character that an HTTP header does not accept" }
    withContext(Dispatchers.IO) { credentialStore().setPassword(kind.credentialAttributes, value) }
    key = value
    isLoaded = true
    rejectedKey.set(null)
  }

  /**
   * Handles a refusal of the API for [refusedKey]. See [SeDecisionsEndpointLease.refused].
   *
   * The store serves no rejected key (401 or 403) until the user sets a key again. A rate or a quota limit (429) keeps the key.
   * The user gets one notification for each rejected key, and one for the first limit.
   */
  fun refused(refusedKey: String, statusCode: Int) {
    val bundle = SearchEverywhereFrontendBundle.bundle
    when (statusCode) {
      401, 403 -> if (rejectedKey.getAndSet(refusedKey) != refusedKey) {
        notifyUser(bundle.getMessage(kind.rejectedTitleKey), bundle.getMessage(kind.rejectedContentKey))
      }
      429 -> if (isLimitReported.compareAndSet(false, true)) {
        notifyUser(bundle.getMessage(kind.limitTitleKey), bundle.getMessage(kind.limitContentKey))
      }
    }
  }

  private suspend fun loadKey(): String? = mutex.withLock {
    if (!isLoaded) {
      val stored = withContext(Dispatchers.IO) { credentialStore().getPassword(kind.credentialAttributes) }?.trim()?.ifEmpty { null }
      if (stored != null && !isValidApiKey(stored)) {
        // The report names no part of the key.
        LOG.warn("The stored ${kind.keyName} of the Search Everywhere ranker has a character that an HTTP header does not accept")
      }
      key = stored?.takeIf(::isValidApiKey)
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

/**
 * Returns the endpoint at [url] that sends each request with the personal [key] as a bearer token.
 * It reports a refusal to [keyStore].
 */
internal fun apiKeyLease(url: String, key: String, keyStore: SeApiKeyStore): SeDecisionsEndpointLease = SeApiKeyLease(url, key, keyStore)

/** The endpoint of an API with a personal key. It holds nothing to release. */
private class SeApiKeyLease(
  override val url: String,
  private val key: String,
  private val keyStore: SeApiKeyStore,
) : SeDecisionsEndpointLease {
  override val headers: Map<String, String> = mapOf(AUTHORIZATION_HEADER to "Bearer $key")

  override suspend fun close() {}

  override fun refused(statusCode: Int) {
    keyStore.refused(key, statusCode)
  }

  override fun toString(): String = "the API at $url with the personal ${keyStore.kind.keyName} (headers=${headers.keys})"
}
