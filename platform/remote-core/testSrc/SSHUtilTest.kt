// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ssh;

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.regex.Matcher
import java.util.regex.Pattern

class SSHUtilTest {
  @Test
  fun testPassphraseRegex() {
    passphraseRegexShouldMatch("Enter passphrase for key 'С:\\test\\dir\\.ssh\\id.rsa':", "С:\\test\\dir\\.ssh\\id.rsa")
    passphraseRegexShouldMatch("Enter passphrase for 'С:\\test\\dir\\.ssh\\id.rsa':", "С:\\test\\dir\\.ssh\\id.rsa")
    passphraseRegexShouldMatch("Enter passphrase for key '/home/test/rsa':", "/home/test/rsa")
    passphraseRegexShouldMatch("Enter passphrase for '/home/test/rsa':", "/home/test/rsa")
  }

  @Test
  fun testPasswordRegex() {
    passwordRegexShouldMatch("User1's password:", "User1")
  }

  private fun passphraseRegexShouldMatch(input: String, expected: String) {
    return regexShouldMatch(SSHUtil.PASSPHRASE_PROMPT, input, expected) { SSHUtil.extractKeyPath(it) }
  }

  private fun passwordRegexShouldMatch(input: String, expected: String) {
    return regexShouldMatch(SSHUtil.PASSWORD_PROMPT, input, expected) { SSHUtil.extractUsername(it) }
  }
  private fun regexShouldMatch(pattern: Pattern, input: String, expected: String, resultProvider: (Matcher) -> String) {
    val matcher = pattern.matcher(input)
    Assertions.assertTrue(matcher.matches())
    val result = resultProvider(matcher)
    Assertions.assertEquals(expected, result)
  }
}