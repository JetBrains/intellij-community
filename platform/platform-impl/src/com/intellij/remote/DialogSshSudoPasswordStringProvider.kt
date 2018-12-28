// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.askPassword

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

  override fun provide(allowCache: Boolean): String? {
    return askPassword(project = null,
                       dialogTitle = "Enter Sudo password",
                       passwordFieldLabel = sshPath,
                       attributes = credentialAttributes,
                       resetPassword = !allowCache)
  }
}
