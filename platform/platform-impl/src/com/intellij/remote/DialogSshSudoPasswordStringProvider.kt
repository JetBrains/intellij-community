// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.askCredentials
import com.intellij.ide.passwordSafe.PasswordSafe

class DialogSshSudoPasswordStringProvider : PasswordStringProvider {
  private val sshPath: String
  private val credentialAttributes: CredentialAttributes

  constructor(sshPath: String) {
    this.sshPath = sshPath
    credentialAttributes = CredentialAttributes("IDEA sudo for ${sshPath}")
  }

  constructor(data: RemoteSdkCredentials) {
    with(data) {
      val hostPort = if (port == 22) host else "${host}:${port}"
      sshPath = "ssh://${userName}@${hostPort}"
    }
    credentialAttributes = CredentialAttributes("IDEA sudo for ${sshPath}")
  }

  override fun provide(tryGetFromStore: Boolean, tellThatPreviousPasswordWasWrong: Boolean): PasswordStringProvider.PasswordResult? {
    if (tryGetFromStore) {
      val store = PasswordSafe.instance
      store.get(credentialAttributes)?.let {
        it.getPasswordAsString()?.let {
          return PasswordStringProvider.PasswordResult(it, true)
        }
      }
    }
    val result = askCredentials(
      project = null,
      dialogTitle = "Enter Sudo password",
      passwordFieldLabel = sshPath,
      attributes = credentialAttributes,
      error = if (tellThatPreviousPasswordWasWrong) "Sorry, try again." else null)
    return if (result == null) {
      // Pressed "Cancel"
      null
    }
    else when (val password = result.credentials.getPasswordAsString()) {
      null -> {
        // Pressed "OK", but when user writes empty password (that is a correct password)
        // [askCredentials] returns null instead of empty string.
        PasswordStringProvider.PasswordResult("", false)
      }
      else -> PasswordStringProvider.PasswordResult(password, false)
    }
  }
}
