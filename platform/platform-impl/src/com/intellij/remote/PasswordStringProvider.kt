// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote

@FunctionalInterface
interface PasswordStringProvider {
  /**
   * Ask user for password.
   *
   * @param allowCache May password be taken from some cache like keychain, keepass, etc.
   * @return Password string or null when user refused to provide password.
   */
  fun provide(allowCache: Boolean): String?
}
