@file:JvmName("SensitiveDataUtil")

/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remote

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

val PASSWORD_QUALIFIER = "password"
val PASSPHRASE_QUALIFIER = "passphrase"

fun RemoteCredentials.clearPassword() {
  storePassword(password = null, memoryOnly = false)
  storePassword(password = null, memoryOnly = true)
}

fun RemoteCredentials.clearPassphrase() {
  storePassphrase(passphrase = null, memoryOnly = false)
  storePassphrase(passphrase = null, memoryOnly = true)
}

fun RemoteCredentials.isPasswordSaved(): Boolean {
  return isSensitiveDataSaved(PASSWORD_QUALIFIER)
}

fun RemoteCredentials.isPassphraseSaved(): Boolean {
  return isSensitiveDataSaved(PASSPHRASE_QUALIFIER)
}

private fun RemoteCredentials.isSensitiveDataSaved(qualifier: String): Boolean {
  val attributes = createAttributes(qualifier = qualifier, isPasswordMemoryOnly = false)
  val credentials = PasswordSafe.getInstance().get(attributes)
  return credentials?.password != null
}

fun RemoteCredentials.loadPassword(): String? = loadSensitiveData(PASSWORD_QUALIFIER)

fun RemoteCredentials.loadPassphrase(): String? = loadSensitiveData(PASSPHRASE_QUALIFIER)

fun RemoteCredentials.storePassword(password: String?, memoryOnly: Boolean) {
  storeSensitiveData(this, PASSWORD_QUALIFIER, password, memoryOnly)
}

fun RemoteCredentials.storePassphrase(passphrase: String?, memoryOnly: Boolean) {
  storeSensitiveData(this, PASSPHRASE_QUALIFIER, passphrase, memoryOnly)
}

fun RemoteCredentials.loadSensitiveData(qualifier: String): String? {
  return loadSensitiveData(userName = userName,
                           host = host,
                           port = literalPort,
                           qualifier = qualifier)
}

fun loadSensitiveData(userName: String?, host: String?, port: String?, qualifier: String): String? {
  val attributes = createAttributes(userName = userName,
                                    host = host,
                                    port = port,
                                    qualifier = qualifier,
                                    isPasswordMemoryOnly = false)
  val sensitiveCredentials = PasswordSafe.getInstance().get(attributes)
  return sensitiveCredentials?.getPasswordAsString()
}

fun storeSensitiveData(remoteCredentials: RemoteCredentials,
                       qualifier: String,
                       data: String?,
                       passwordMemoryOnly: Boolean) {
  // todo memory only or not?
  val attributes = createAttributes(userName = remoteCredentials.userName,
                                    host = remoteCredentials.host,
                                    port = remoteCredentials.literalPort,
                                    qualifier = qualifier,
                                    isPasswordMemoryOnly = passwordMemoryOnly)
  val sensitiveCredentials = Credentials(remoteCredentials.userName, data)
  PasswordSafe.getInstance().set(attributes, sensitiveCredentials)
}

private fun RemoteCredentials.createAttributes(qualifier: String, isPasswordMemoryOnly: Boolean): CredentialAttributes {
  return createAttributes(userName = userName,
                          host = host,
                          port = literalPort,
                          qualifier = qualifier,
                          isPasswordMemoryOnly = isPasswordMemoryOnly)
}

private fun createAttributes(userName: String?,
                             host: String?,
                             port: String?,
                             qualifier: String,
                             isPasswordMemoryOnly: Boolean): CredentialAttributes {
  val credentialsString = RemoteCredentialsHolder.getCredentialsString(userName, host, port)
  val serviceName = "${RemoteSdkCredentialsHolder.SERVICE_NAME_PREFIX}$credentialsString($qualifier)"
  return CredentialAttributes(serviceName, userName, null, isPasswordMemoryOnly)
}