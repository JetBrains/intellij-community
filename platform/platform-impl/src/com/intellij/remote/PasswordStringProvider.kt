// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote

@FunctionalInterface
interface PasswordStringProvider {
  /**
   * @param password The password.
   * @param takenFromStore Indicates that password was taken from some
   *                       store and user did not seen any password request.
   */
  data class PasswordResult(val password: String, val takenFromStore: Boolean)

  /**
   * Ask user for password.
   *
   * @param tryGetFromStore May password be taken from some store like keychain, keepass, etc.
   * @return Either object with password or null if user refuses to provide password.
   */
  fun provide(tryGetFromStore: Boolean, tellThatPreviousPasswordWasWrong: Boolean = false): PasswordResult?
}
