// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote

import java.time.Duration

/**
 * Additional options overridable in SSH Connections settings. This config must have the highest priority across all other configuration
 * ways. Every field is nullable. Null means that the value should keep its default value.
 *
 * @param serverAliveInterval How often to send keep-alive messages in OpenSSH format. Overrides `ServerAliveInterval` section of
 *  OpenSSH configs. If the duration is zero or negative, keep-alive messages are forcibly disabled.
 */
data class SshConnectionConfigPatch(
  var hostKeyVerifier: HostKeyVerifier?,
  var serverAliveInterval: Duration?,
  var proxyParams: ProxyParams?,
) {
  data class ProxyParams(
    var proxyHost: String,
    var proxyPort: Int,
    var proxyType: Type,
    var authData: ProxyAuthData?,
  ) {
    constructor() : this(proxyHost = "", proxyPort = -1, Type.NO_PROXY, authData = null)

    enum class Type {
      NO_PROXY,
      HTTP,
      SOCKS,
      IDE_WIDE_PROXY
    }

    data class ProxyAuthData(
      var username: String,
      var password: String,
      var authType: ProxyAuthType,
    ) {
      constructor() : this(username = "", password = "", ProxyAuthType.NO_AUTHORIZATION)

      enum class ProxyAuthType {
        NO_AUTHORIZATION,
        USER_AND_PASSWORD,
      }
    }
  }

  /**
   * @param hashKnownHosts Indicates that host names and addresses should be hashed while being added to the known hosts file.
   * @param strictHostKeyChecking How the SSH client should react to a host key which is absent from the known hosts file.
   */
  data class HostKeyVerifier(
    var hashKnownHosts: Boolean?,
    var strictHostKeyChecking: StrictHostKeyChecking?,
  ) {
    constructor() : this(hashKnownHosts = null, strictHostKeyChecking = null)

    enum class StrictHostKeyChecking {
      /** Never automatically add host keys to the known hosts file. */
      YES,

      /** Automatically add new host keys to the known hosts file but do not permit connections to hosts with changed host keys. */
      ACCEPT_NEW,

      /** Automatically add new host keys to the known hosts file and allow connections to hosts with changed host keys to proceed. */
      NO,

      /** New host keys will be added to the known host file only after the user has confirmed that is what they really want to do. */
      ASK,
    }
  }

  constructor() : this(hostKeyVerifier = null, serverAliveInterval = null, proxyParams = null)

  fun deepCopy(): SshConnectionConfigPatch = copy(
    hostKeyVerifier = hostKeyVerifier?.copy(),
    proxyParams = proxyParams?.copy(),
  )
}
