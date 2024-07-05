// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import java.security.SecureRandom

object PinentryTestUtil {
  private const val UPPERCASE_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
  private const val LOWERCASE_LETTERS = "abcdefghijklmnopqrstuvwxyz"
  private const val DIGITS = "0123456789"
  private const val SPECIAL_CHARACTERS = "!@#$%^&*()-_=+[]{}|;:,.<>?/"

  const val ALL_CHARACTERS = UPPERCASE_LETTERS + LOWERCASE_LETTERS + DIGITS + SPECIAL_CHARACTERS

  private val RANDOM = SecureRandom()

  fun generatePassword(length: Int): String {
    val password = StringBuilder(length)

    //fill first 4 char
    password.append(UPPERCASE_LETTERS[RANDOM.nextInt(UPPERCASE_LETTERS.length)])
    password.append(LOWERCASE_LETTERS[RANDOM.nextInt(LOWERCASE_LETTERS.length)])
    password.append(DIGITS[RANDOM.nextInt(DIGITS.length)])
    password.append(SPECIAL_CHARACTERS[RANDOM.nextInt(SPECIAL_CHARACTERS.length)])

    //fill last characters randomly
    repeat(length - 4) {
      password.append(ALL_CHARACTERS[RANDOM.nextInt(ALL_CHARACTERS.length)])
    }

    require(password.length == length)

    return password.toString().shuffle()
  }

  private fun String.shuffle(): String {
    val characters = this.toCharArray()

    for (i in characters.indices) {
      val randomIndex = RANDOM.nextInt(characters.size)
      val temp = characters[i]
      characters[i] = characters[randomIndex]
      characters[randomIndex] = temp
    }

    return String(characters)
  }
}
