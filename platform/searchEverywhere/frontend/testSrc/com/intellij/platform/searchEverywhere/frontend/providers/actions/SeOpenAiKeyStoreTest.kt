// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.Credentials
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CopyOnWriteArrayList

@TestApplication
class SeOpenAiKeyStoreTest {
  private val credentials = FakeCredentialStore()

  private val store = SeOpenAiKeyStore { credentials }

  @Test
  fun `a key is trimmed, and a blank value removes it`() = runBlocking {
    store.setKey("  sk-test  ")

    assertEquals("sk-test", store.getKey())
    assertEquals("sk-test", credentials.password)

    store.setKey(" ")

    assertNull(store.getKey())
    assertNull(credentials.password)
  }

  @Test
  fun `a key with a character that a header does not accept is refused`() {
    assertThrows<IllegalArgumentException> {
      runBlocking { store.setKey("sk-​test") }
    }
    assertNull(credentials.password)
  }

  @Test
  fun `a stored key with a character that a header does not accept is not served`() = runBlocking {
    credentials.password = "sk-​test"

    assertNull(store.getKey())
  }

  @Test
  fun `a rejected key is not served until the user sets a key again`(@TestDisposable disposable: Disposable) = runBlocking {
    val notifications = collectNotifications(disposable)
    store.setKey("sk-old")

    store.refused("sk-old", 401)
    store.refused("sk-old", 403)

    assertNull(store.getKey())
    assertEquals(1, notifications.size)

    store.setKey("sk-new")

    assertEquals("sk-new", store.getKey())
  }

  @Test
  fun `a limit keeps the key and notifies the user once`(@TestDisposable disposable: Disposable) = runBlocking {
    val notifications = collectNotifications(disposable)
    store.setKey("sk-test")

    store.refused("sk-test", 429)
    store.refused("sk-test", 429)

    assertEquals("sk-test", store.getKey())
    assertEquals(1, notifications.size)
  }

  @Test
  fun `a refusal through the lease reaches the store`() = runBlocking {
    store.setKey("sk-test")

    openAiKeyLease("sk-test", store).refused(401)

    assertNull(store.getKey())
  }

  private fun collectNotifications(disposable: Disposable): List<Notification> {
    val notifications = CopyOnWriteArrayList<Notification>()
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        notifications += notification
      }
    })
    return notifications
  }

  private class FakeCredentialStore : CredentialStore {
    @Volatile
    var password: String? = null

    override fun get(attributes: CredentialAttributes): Credentials? = password?.let { Credentials(attributes.userName, it) }

    override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
      password = credentials?.getPasswordAsString()
    }
  }
}
