// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.http

import com.intellij.collaboration.api.ServerPath
import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.DefaultAccountHolder
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.AuthData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.URI

@TestApplication
internal class SilentHostedGitHttpAuthDataProviderBaseTest {

  private val project = mockk<Project>()

  @Test
  fun `getAuthData returns credentials of the matching default account`() {
    val account = account("default-user")
    val provider = provider(accountManager(mapOf(account to "default-token")), defaultAccount = account)

    val authData = provider.getAuthData(project, URL)

    assertNotNull(authData)
    assertEquals("default-user", authData!!.login)
    assertEquals("default-token", authData.password)
  }

  @Test
  fun `getAuthData falls back to the single matching account when there is no default`() {
    val account = account("solo-user")
    val provider = provider(accountManager(mapOf(account to "solo-token")), defaultAccount = null)

    val authData = provider.getAuthData(project, URL)

    assertNotNull(authData)
    assertEquals("solo-user", authData!!.login)
    assertEquals("solo-token", authData.password)
  }

  @Test
  fun `getAuthData falls back to accounts when the default account is on a different host`() {
    val defaultAccount = account("default-user", OTHER_SERVER)
    val account = account("solo-user")
    val provider = provider(
      accountManager(mapOf(defaultAccount to "default-token", account to "solo-token")),
      defaultAccount = defaultAccount,
    )

    val authData = provider.getAuthData(project, URL)

    assertNotNull(authData)
    assertEquals("solo-user", authData!!.login)
    assertEquals("solo-token", authData.password)
  }

  @Test
  fun `getAuthData returns null when there are no accounts`() {
    val provider = provider(accountManager(emptyMap()))
    assertNull(provider.getAuthData(project, URL))
  }

  @Test
  fun `getAuthData returns null when several accounts have credentials and there is no default`() {
    val provider = provider(accountManager(mapOf(account("a") to "token-a", account("b") to "token-b")))
    assertNull(provider.getAuthData(project, URL))
  }

  @Test
  fun `getAuthData returns null when the default account has no stored credentials`() {
    val account = account("default-user")
    val provider = provider(accountManager(mapOf(account to null)), defaultAccount = account)
    assertNull(provider.getAuthData(project, URL))
  }

  @Test
  fun `getAuthData returns null when auth data getter yields null`() {
    val account = account("default-user")
    val provider = provider(
      accountManager(mapOf(account to "default-token")),
      defaultAccount = account,
      getAuthData = { _ -> null },
    )
    assertNull(provider.getAuthData(project, URL))
  }

  @Test
  fun `getAuthData skips the default account that is marked as failed`() {
    val account = account("default-user")
    val provider = provider(
      accountManager(mapOf(account to "default-token")),
      defaultAccount = account,
      failureManager = failureManager { _, acc -> acc == account },
    )
    assertNull(provider.getAuthData(project, URL))
  }

  @Test
  fun `getAuthData by login returns the default account when the login matches`() {
    val account = account("default-user")
    val provider = provider(accountManager(mapOf(account to "default-token")), defaultAccount = account)

    val authData = provider.getAuthData(project, URL, "default-user")

    assertNotNull(authData)
    assertEquals("default-user", authData!!.login)
    assertEquals("default-token", authData.password)
  }

  @Test
  fun `getAuthData by login falls back to another account when the default login does not match`() {
    val defaultAccount = account("default-user")
    val otherAccount = account("other-user")
    val provider = provider(
      accountManager(mapOf(defaultAccount to "default-token", otherAccount to "other-token")),
      defaultAccount = defaultAccount,
    )

    val authData = provider.getAuthData(project, URL, "other-user")

    assertNotNull(authData)
    assertEquals("other-user", authData!!.login)
    assertEquals("other-token", authData.password)
  }

  @Test
  fun `getAuthData by login returns null when no account matches the login`() {
    val account = account("some-user")
    val provider = provider(accountManager(mapOf(account to "some-token")), defaultAccount = account)
    assertNull(provider.getAuthData(project, URL, "unknown-user"))
  }

  @Test
  fun `forgetPassword marks the account produced by the same provider as failed`() {
    val account = account("solo-user")
    val failureManager = failureManager()
    val provider = provider(accountManager(mapOf(account to "solo-token")), failureManager = failureManager)

    val authData = provider.getAuthData(project, URL)
    assertNotNull(authData)

    provider.forgetPassword(project, URL, authData!!)

    verify(exactly = 1) { failureManager.ignoreAccount(URL, account) }
  }

  @Test
  fun `forgetPassword ignores auth data that is not produced by this provider`() {
    val failureManager = failureManager()
    val provider = provider(accountManager(emptyMap()), failureManager = failureManager)

    provider.forgetPassword(project, URL, AuthData("foreign-user", "foreign-token"))

    verify(exactly = 0) { failureManager.ignoreAccount(any(), any()) }
  }

  @Test
  fun `forgetPassword ignores auth data produced by a provider with a different id`() {
    val account = account("solo-user")
    val accountManager = accountManager(mapOf(account to "solo-token"))
    val producer = provider(accountManager, providerId = "producer-id")
    val failureManager = failureManager()
    val consumer = provider(accountManager, failureManager = failureManager, providerId = "consumer-id")

    val authData = producer.getAuthData(project, URL)
    assertNotNull(authData)

    consumer.forgetPassword(project, URL, authData!!)

    verify(exactly = 0) { failureManager.ignoreAccount(any(), any()) }
  }

  @Test
  fun `getAccountsWithTokens keeps only host-matching, non-ignored accounts and maps their credentials`() = timeoutRunBlocking {
    val matching = account("match")
    val matchingWithoutToken = account("no-token")
    val ignored = account("ignored")
    val otherHost = account("other", OTHER_SERVER)
    val accountManager = accountManager(
      mapOf(
        matching to "token",
        matchingWithoutToken to null,
        ignored to "ignored-token",
        otherHost to "other-token",
      )
    )
    val failureManager = failureManager { _, acc -> acc == ignored }

    val result = SilentHostedGitHttpAuthDataProviderBase.getAccountsWithTokens(accountManager, failureManager, URL)

    assertEquals(setOf(matching, matchingWithoutToken), result.keys)
    assertEquals("token", result[matching])
    assertNull(result[matchingWithoutToken])
    assertFalse(result.containsKey(ignored))
    assertFalse(result.containsKey(otherHost))
  }

  private fun provider(
    accountManager: AccountManager<TestServerAccount, String>,
    defaultAccount: TestServerAccount? = null,
    failureManager: HostedGitAuthenticationFailureManager<TestServerAccount> = failureManager(),
    providerId: String = PROVIDER_ID,
    getAuthData: suspend (TestServerAccount) -> AuthData? = { account ->
      accountManager.findCredentials(account)?.let {
        AuthData(account.name, it)
      }
    },
  ): TestSilentProvider =
    TestSilentProvider(providerId, accountManager, defaultAccountHolder(defaultAccount), failureManager, getAuthData)

  private fun accountManager(accounts: Map<TestServerAccount, String?>): AccountManager<TestServerAccount, String> = mockk {
    every { accountsState } returns MutableStateFlow(accounts.keys)
    coEvery { findCredentials(any()) } answers { accounts[firstArg<TestServerAccount>()] }
  }

  private fun defaultAccountHolder(account: TestServerAccount?): DefaultAccountHolder<TestServerAccount> = mockk {
    every { this@mockk.account } returns account
  }

  private fun failureManager(
    isIgnored: (url: String, account: TestServerAccount) -> Boolean = { _, _ -> false },
  ): HostedGitAuthenticationFailureManager<TestServerAccount> = mockk(relaxed = true) {
    every { isAccountIgnored(any(), any()) } answers { isIgnored(firstArg(), secondArg()) }
  }

  private fun account(name: String, server: ServerPath = SERVER): TestServerAccount = TestServerAccount(name = name, server = server)

  private class TestSilentProvider(
    override val providerId: String,
    override val accountManager: AccountManager<TestServerAccount, String>,
    private val defaultAccountHolder: DefaultAccountHolder<TestServerAccount>,
    private val failureManager: HostedGitAuthenticationFailureManager<TestServerAccount>,
    private val authDataExtractor: suspend (TestServerAccount) -> AuthData?,
  ) : SilentHostedGitHttpAuthDataProviderBase<TestServerAccount, String>() {
    override fun getDefaultAccountHolder(project: Project): DefaultAccountHolder<TestServerAccount> = defaultAccountHolder
    override fun getAuthFailureManager(project: Project): HostedGitAuthenticationFailureManager<TestServerAccount> = failureManager
    override suspend fun getAuthData(account: TestServerAccount): AuthData? =
      authDataExtractor(account)
  }

  private class TestServerAccount(
    override val id: String = generateId(),
    override val name: String,
    override val server: ServerPath,
  ) : ServerAccount()

  private class TestServerPath(private val uri: URI) : ServerPath {
    override fun toURI(): URI = uri
    override fun toString(): String = uri.toString()
  }

  private companion object {
    private const val PROVIDER_ID = "test-provider"
    private const val URL = "https://example.com/user/repo.git"
    private val SERVER: ServerPath = TestServerPath(URI("https://example.com"))
    private val OTHER_SERVER: ServerPath = TestServerPath(URI("https://other.example.org"))
  }
}
