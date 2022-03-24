// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

const val SERVICE_NAME_PREFIX = "IntelliJ Platform"

/**
 * The combined name of your service and name of service that requires authentication.
 *
 * Can be specified in:
 * * reverse-DNS format: `com.apple.facetime: registrationV1`
 * * prefixed human-readable format: `IntelliJ Platform Settings Repository — github.com`, where `IntelliJ Platform` is a required prefix. **You must always use this prefix**.
 */
fun generateServiceName(subsystem: String, key: String) = "$SERVICE_NAME_PREFIX $subsystem — $key"

/**
 * Consider using [generateServiceName] to generate [serviceName].
 *
 * [requestor] is deprecated (never use it in a new code).
 */
data class CredentialAttributes(
  val serviceName: String,
  val userName: String? = null,
  val requestor: Class<*>? = null,
  val isPasswordMemoryOnly: Boolean = false,
  val cacheDeniedItems: Boolean = true
) {
  @JvmOverloads
  constructor(serviceName: String,
              userName: String? = null,
              requestor: Class<*>? = null,
              isPasswordMemoryOnly: Boolean = false)
    : this(serviceName, userName, requestor, isPasswordMemoryOnly, true)
}

/**
 * Pair of user and password.
 *
 * @param user Account name ("John") or path to SSH key file ("/Users/john/.ssh/id_rsa").
 * @param password Can be empty.
 */
class Credentials(@NlsSafe user: String?, @NlsSafe val password: OneTimeString? = null) {
  constructor(@NlsSafe user: String?, @NlsSafe password: String?) : this(user, password?.let(::OneTimeString))

  constructor(@NlsSafe user: String?, password: CharArray?) : this(user, password?.let { OneTimeString(it) })

  constructor(@NlsSafe user: String?, password: ByteArray?) : this(user, password?.let { OneTimeString(password) })

  val userName = user.nullize()

  @NlsSafe
  fun getPasswordAsString() = password?.toString()

  override fun equals(other: Any?) = other is Credentials && userName == other.userName && password == other.password

  override fun hashCode() = (userName?.hashCode() ?: 0) * 37 + (password?.hashCode() ?: 0)

  override fun toString() = "userName: $userName, password size: ${password?.length ?: 0}"

  companion object {
    val ACCESS_TO_KEY_CHAIN_DENIED = Credentials(null, null as OneTimeString?)
    val CANNOT_UNLOCK_KEYCHAIN = Credentials(null, null as OneTimeString?)
  }
}

/** @deprecated Use [CredentialAttributes] instead. */
@Deprecated("Never use it in a new code.")
@ApiStatus.ScheduledForRemoval
@Suppress("FunctionName", "DeprecatedCallableAddReplaceWith")
fun CredentialAttributes(requestor: Class<*>, userName: String?) = CredentialAttributes(requestor.name, userName, requestor)

@Contract("null -> false")
fun Credentials?.isFulfilled() = this != null && userName != null && !password.isNullOrEmpty()

@Contract("null -> false")
fun Credentials?.hasOnlyUserName() = this != null && userName != null && password.isNullOrEmpty()

fun Credentials?.isEmpty() = this == null || (userName == null && password.isNullOrEmpty())