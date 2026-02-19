// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote

enum class AuthType {
  PASSWORD, KEY_PAIR,
  /**
   * Use the way OpenSSH `ssh` client authenticates:
   * - read OpenSSH configuration files, get `IdentityFile` declared in it;
   * - use identity files provided by authentication agent (ssh-agent or PuTTY).
   */
  OPEN_SSH;

  val displayName: String
    get() = when (this) {
      PASSWORD -> RemoteBundle.message("display.name.password")
      KEY_PAIR -> RemoteBundle.message("display.name.key.pair.openssh.or.putty")
      OPEN_SSH -> RemoteBundle.message("display.name.openssh.config.and.authentication.agent")
    }
}
