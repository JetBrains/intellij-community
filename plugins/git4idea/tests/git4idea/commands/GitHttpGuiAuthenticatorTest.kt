// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import com.intellij.credentialStore.Credentials
import com.intellij.dvcs.DvcsRememberedInputs
import com.intellij.externalProcessAuthHelper.AuthenticationMode
import com.intellij.externalProcessAuthHelper.PassthroughAuthenticationGate
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.ui.laf.setEarlyUiLaF
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import git4idea.commands.GitHttpGuiAuthenticator.PasswordSafeProvider.credentialAttributes
import git4idea.commands.GitHttpGuiAuthenticator.PasswordSafeProvider.makeKey
import git4idea.remote.GitRememberedInputs
import git4idea.test.GitPlatformTestContext
import git4idea.test.TestDialogHandler
import git4idea.test.gitPlatformContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class GitHttpGuiAuthenticatorTest {
  private val fixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  private lateinit var rememberedInputs: DvcsRememberedInputs
  private lateinit var passwordSafe: PasswordSafe

  private var dialogShown = false

  @BeforeEach
  fun setUp() {
    // otherwise login dialog doesn't work (missing LaF for JBOptionButton)
    setEarlyUiLaF()

    rememberedInputs = service<GitRememberedInputs>()
    passwordSafe = service()
    Disposer.register(disposable) {
      dialogShown = false
      rememberedInputs.clear()
      passwordSafe[CREDENTIAL_ATTRIBUTES] = null
    }
  }

  @Test
  fun `test data saved when correct`() {
    registerDialogHandler(true)

    runAuthenticator(true)

    assertThat(dialogShown).isTrue()
    assertSavedPasswordEquals(TEST_PASSWORD)
    assertThat(rememberedInputs.getUserNameForUrl(TEST_URL)).isEqualTo(TEST_LOGIN)
  }

  @Test
  fun `test password not saved when incorrect`() {
    registerDialogHandler(true)
    runAuthenticator(false)

    assertThat(dialogShown).isTrue()
    assertSavedPasswordEquals(null)
  }

  @Test
  fun `test incorrect saved password forgotten`() {
    registerDialogHandler(true)

    rememberedInputs.addUrl(TEST_URL, TEST_LOGIN)
    passwordSafe[CREDENTIAL_ATTRIBUTES] = Credentials(TEST_PSAFE_KEY, TEST_PASSWORD)

    runAuthenticator(false)
    assertThat(dialogShown).isFalse()

    assertSavedPasswordEquals(null)
  }

  @Test
  fun `test password not remembered`() {
    registerDialogHandler(true, false)
    runAuthenticator(true)

    assertThat(dialogShown).isTrue()
    assertSavedPasswordEquals(null)
  }

  @Test
  fun `test dialog cancellation propagated`() {
    registerDialogHandler(false)
    val authenticator = runAuthenticator(false)

    assertThat(dialogShown).isTrue()
    assertThat(authenticator.wasCancelled()).isTrue()
    assertSavedPasswordEquals(null)
  }

  @Test
  fun `test single dialog shown`() {
    registerDialogHandler(true)

    val authenticator = GitHttpGuiAuthenticator(context.project, listOf(TEST_URL), Path.of(""),
                                                PassthroughAuthenticationGate.instance,
                                                AuthenticationMode.FULL)
    authenticator.askUsername(TEST_URL)
    assertThat(dialogShown).isTrue()

    dialogShown = false
    authenticator.askPassword(TEST_URL)
    assertThat(dialogShown).isFalse()
  }

  private fun registerDialogHandler(exitOk: Boolean, rememberPassword: Boolean = true) {
    context.dialogManager.registerDialogHandler(GitHttpLoginDialog::class.java, TestDialogHandler {
      dialogShown = true

      it.username = TEST_LOGIN
      it.password = TEST_PASSWORD
      it.rememberPassword = rememberPassword
      if (exitOk) DialogWrapper.OK_EXIT_CODE else DialogWrapper.CANCEL_EXIT_CODE
    })
  }

  private fun runAuthenticator(assumeCorrect: Boolean): GitHttpGuiAuthenticator {
    val authenticator = GitHttpGuiAuthenticator(context.project, listOf(TEST_URL), Path.of(""),
                                                PassthroughAuthenticationGate.instance,
                                                AuthenticationMode.FULL)
    val username = authenticator.askUsername(TEST_URL)
    val password = authenticator.askPassword(TEST_URL)
    if (assumeCorrect) {
      assertThat(username).isEqualTo(TEST_LOGIN)
      assertThat(password).isEqualTo(TEST_PASSWORD)
      authenticator.saveAuthData()
    }
    else {
      authenticator.forgetPassword()
    }
    return authenticator
  }

  private fun assertSavedPasswordEquals(match: String?) {
    assertThat(passwordSafe.getPassword(CREDENTIAL_ATTRIBUTES)).isEqualTo(match)
  }

  companion object {
    private const val TEST_URL = "http://nonexistent.site/repo.git"
    private const val TEST_LOGIN = "smith"
    private const val TEST_PASSWORD = "pwd"

    private val TEST_PSAFE_KEY = makeKey(TEST_URL, TEST_LOGIN)

    private val CREDENTIAL_ATTRIBUTES = credentialAttributes(TEST_PSAFE_KEY)
  }
}