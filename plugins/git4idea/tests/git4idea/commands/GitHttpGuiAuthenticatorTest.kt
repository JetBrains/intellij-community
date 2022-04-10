// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands

import com.intellij.credentialStore.Credentials
import com.intellij.dvcs.DvcsRememberedInputs
import com.intellij.externalProcessAuthHelper.AuthenticationMode
import com.intellij.externalProcessAuthHelper.PassthroughAuthenticationGate
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import git4idea.commands.GitHttpGuiAuthenticator.PasswordSafeProvider.credentialAttributes
import git4idea.commands.GitHttpGuiAuthenticator.PasswordSafeProvider.makeKey
import git4idea.remote.GitRememberedInputs
import git4idea.test.GitPlatformTest
import git4idea.test.TestDialogHandler
import java.io.File
import javax.swing.UIManager

class GitHttpGuiAuthenticatorTest : GitPlatformTest() {
  private lateinit var rememberedInputs: DvcsRememberedInputs
  private lateinit var passwordSafe: PasswordSafe

  private var dialogShown = false

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    // otherwise login dialog doesn't work (missing LaF for JBOptionButton)
    UIManager.setLookAndFeel("com.intellij.ide.ui.laf.darcula.DarculaLaf")

    rememberedInputs = service<GitRememberedInputs>()
    passwordSafe = service()
  }

  @Throws(Exception::class)
  public override fun tearDown() {
    dialogShown = false

    rememberedInputs.clear()
    passwordSafe.set(CREDENTIAL_ATTRIBUTES, null)

    super.tearDown()
  }

  fun `test data saved when correct`() {
    registerDialogHandler(true)

    runAuthenticator(true)

    assertTrue(dialogShown)
    assertSavedPasswordEquals(TEST_PASSWORD)
    assertEquals(TEST_LOGIN, rememberedInputs.getUserNameForUrl(TEST_URL))
  }

  fun `test password not saved when incorrect`() {
    registerDialogHandler(true)
    runAuthenticator(false)

    assertTrue(dialogShown)
    assertSavedPasswordEquals(null)
  }

  fun `test incorrect saved password forgotten`() {
    registerDialogHandler(true)

    rememberedInputs.addUrl(TEST_URL, TEST_LOGIN)
    passwordSafe.set(CREDENTIAL_ATTRIBUTES, Credentials(TEST_PSAFE_KEY, TEST_PASSWORD))

    runAuthenticator(false)
    assertFalse(dialogShown)

    assertSavedPasswordEquals(null)
  }

  fun `test password not remembered`() {
    registerDialogHandler(true, false)
    runAuthenticator(true)

    assertTrue(dialogShown)
    assertSavedPasswordEquals(null)
  }

  fun `test dialog cancellation propagated`() {
    registerDialogHandler(false)
    val authenticator = runAuthenticator(false)

    assertTrue(dialogShown)
    assertTrue(authenticator.wasCancelled())
    assertSavedPasswordEquals(null)
  }

  fun `test single dialog shown`() {
    registerDialogHandler(true)

    val authenticator = GitHttpGuiAuthenticator(project, listOf(TEST_URL), File(""),
                                                PassthroughAuthenticationGate.instance,
                                                AuthenticationMode.FULL)
    authenticator.askUsername(TEST_URL)
    assertTrue(dialogShown)

    dialogShown = false
    authenticator.askPassword(TEST_URL)
    assertFalse(dialogShown)
  }

  private fun registerDialogHandler(exitOk: Boolean, rememberPassword: Boolean = true) {
    dialogManager.registerDialogHandler(GitHttpLoginDialog::class.java, TestDialogHandler {
      dialogShown = true

      it.username = TEST_LOGIN
      it.password = TEST_PASSWORD
      it.rememberPassword = rememberPassword
      if (exitOk) DialogWrapper.OK_EXIT_CODE else DialogWrapper.CANCEL_EXIT_CODE
    })
  }

  private fun runAuthenticator(assumeCorrect: Boolean): GitHttpGuiAuthenticator {
    val authenticator = GitHttpGuiAuthenticator(project, listOf(TEST_URL), File(""),
                                                PassthroughAuthenticationGate.instance,
                                                AuthenticationMode.FULL)
    val username = authenticator.askUsername(TEST_URL)
    val password = authenticator.askPassword(TEST_URL)
    if (assumeCorrect) {
      assertEquals(TEST_LOGIN, username)
      assertEquals(TEST_PASSWORD, password)
      authenticator.saveAuthData()
    }
    else {
      authenticator.forgetPassword()
    }
    return authenticator
  }

  private fun assertSavedPasswordEquals(match: String?) {
    assertEquals(match, passwordSafe.getPassword(CREDENTIAL_ATTRIBUTES))
  }

  companion object {
    private const val TEST_URL = "http://nonexistent.site/repo.git"
    private const val TEST_LOGIN = "smith"
    private const val TEST_PASSWORD = "pwd"

    private val TEST_PSAFE_KEY = makeKey(TEST_URL, TEST_LOGIN)

    private val CREDENTIAL_ATTRIBUTES = credentialAttributes(TEST_PSAFE_KEY)
  }
}