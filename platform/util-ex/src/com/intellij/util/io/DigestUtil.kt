// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import java.security.MessageDigest
import java.security.Provider

object DigestUtil {
  private val sunSecurityProvider: Provider = java.security.Security.getProvider("SUN")

  @JvmStatic
  fun md5() = getMessageDigest("MD5")

  @JvmStatic
  fun sha1() = getMessageDigest("SHA-1")

  @JvmStatic
  fun sha256() = getMessageDigest("SHA-256")

  private fun getMessageDigest(algorithm: String): MessageDigest = MessageDigest.getInstance(algorithm,
                                                                                             sunSecurityProvider)
}