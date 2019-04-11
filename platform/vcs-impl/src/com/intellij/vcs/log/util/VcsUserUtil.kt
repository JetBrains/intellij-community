// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util

import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.text.StringUtil
import com.intellij.vcs.log.VcsUser
import java.util.*
import java.util.regex.Pattern

object VcsUserUtil {
  private val NAME_PATTERN = Pattern.compile("(\\w+)[\\W_](\\w+)")
  private val PRINTABLE_ASCII_PATTERN = Pattern.compile("[ -~]*")

  @JvmStatic
  fun toExactString(user: VcsUser): String = getString(user.name, user.email)

  private fun getString(name: String, email: String): String =
    when {
      name.isEmpty() -> email
      email.isEmpty() -> name
      else -> "$name <$email>"
    }

  @JvmStatic
  fun isSamePerson(user1: VcsUser, user2: VcsUser): Boolean =
    getNameInStandardForm(getName(user1)) == getNameInStandardForm(getName(user2))

  @JvmStatic
  fun getShortPresentation(user: VcsUser): String = getName(user)

  private fun getName(user: VcsUser): String = getUserName(user.name, user.email)

  @JvmStatic
  fun getUserName(name: String, email: String): String =
    if (!name.isEmpty()) {
      name
    }
    else {
      getNameFromEmail(email) ?: email
    }

  @JvmStatic
  fun getNameFromEmail(email: String): String? = email.substringBefore('@', "").takeIf { it.isNotBlank() }

  @JvmStatic
  fun getNameInStandardForm(name: String): String {
    val firstAndLastName = getFirstAndLastName(name) ?: return nameToLowerCase(name)

    return "${firstAndLastName.first.toLowerCase(Locale.ENGLISH)} ${firstAndLastName.second.toLowerCase(Locale.ENGLISH)}"
  }

  @JvmStatic
  fun getFirstAndLastName(name: String): Couple<String>? {
    val matcher = NAME_PATTERN.matcher(name).takeIf { it.matches() } ?: return null

    return Couple.of(matcher.group(1), matcher.group(2))
  }

  private inline fun convertIfPrintable(string: String, converter: (String) -> String): String =
    if (PRINTABLE_ASCII_PATTERN.matcher(string).matches()) {
      converter(string)
    }
    else {
      string
    }

  @JvmStatic
  fun nameToLowerCase(name: String): String = convertIfPrintable(name) {
    it.toLowerCase(Locale.ENGLISH)
  }

  @JvmStatic
  fun capitalizeName(name: String): String = convertIfPrintable(name) {
    StringUtil.capitalize(it)
  }

  @JvmStatic
  fun emailToLowerCase(email: String): String = email.toLowerCase(Locale.ENGLISH)
}
