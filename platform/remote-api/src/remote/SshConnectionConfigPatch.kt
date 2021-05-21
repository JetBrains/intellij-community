// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote

import org.jetbrains.annotations.ApiStatus
import java.time.Duration

/**
 * Additional options overridable in SSH Connections settings. This config must have the highest priority across all other configuration
 * ways. Every field is nullable. Null means that the value should keep its default value.
 *
 * @param serverAliveInterval How often to send keep-alive messages in OpenSSH format. Overrides `ServerAliveInterval` section of
 *  OpenSSH configs. If the duration is zero or negative, keep-alive messages are forcibly disabled.
 */
@ApiStatus.Experimental
data class SshConnectionConfigPatch(
  var hostKeyVerifier: HostKeyVerifier?,
  var serverAliveInterval: Duration?,
) {
  /**
   * @param hashKnownHosts Indicates that host names and addresses should be hashed while being added to the known hosts file.
   * @param strictHostKeyChecking How the SSH client should react on a host key which's not mentioned in the known hosts file.
   */
  data class HostKeyVerifier(
    var hashKnownHosts: Boolean?,
    var strictHostKeyChecking: StrictHostKeyChecking?,
  ) {
    constructor() : this(null, null)

    fun withHashKnownHosts(value: Boolean): HostKeyVerifier = apply { hashKnownHosts = value }
    fun withStrictHostKeyChecking(value: StrictHostKeyChecking): HostKeyVerifier = apply { strictHostKeyChecking = value }
  }

  constructor() : this(
    hostKeyVerifier = null,
    serverAliveInterval = null,
  )

  fun withHostKeyVerifier(value: HostKeyVerifier): SshConnectionConfigPatch = apply { hostKeyVerifier = value }
  fun withServerAliveInterval(value: Duration): SshConnectionConfigPatch = apply { serverAliveInterval = value }

  fun deepCopy(): SshConnectionConfigPatch = copy(
    hostKeyVerifier = hostKeyVerifier?.copy(),
  )
}

@ApiStatus.Experimental
enum class StrictHostKeyChecking {
  /** Never automatically add host keys to the known hosts file. */
  YES,

  /** Automatically add new host keys to the user known hosts files, but not permit connections to hosts with changed host keys. */
  ACCEPT_NEW,

  /** Automatically add new host keys to the user known hosts files and allow connections to hosts with changed host keys to proceed. */
  NO,

  /** New host keys will be added to the user known host files only after the user has confirmed that is what they really want to do. */
  ASK,
}