// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.teamCity

private val OFFSET: Int = "AAAAAAA".hashCode()

/**
 * 0340430340434 => XDMSLS
 */
fun convertToHashCodeWithOnlyLetters(hash: Int): String {
  val offsettedHash = (hash - OFFSET).toLong()
  var longHash: Long = offsettedHash and 0xFFFFFFFFL

  val generatedChars = CharArray(7)
  val aChar = 'A'

  generatedChars.indices.forEach { index ->
    generatedChars[6 - index] = (aChar + ((longHash % 31).toInt()))
    longHash /= 31
  }

  return generatedChars.filter { it.isLetterOrDigit() }.joinToString(separator = "")
}

/**
 * Simplifies test grouping by replacing numbers, hashes, hex numbers with predefined constant values <ID>, <HASH>, <HEX>
 *  Eg:
 *  /mnt/build/temp35321324432 => <FILE>
 *  text@3ba5aac, text => text<ID>, text
 *  some-text.db451f59 => some-text.<HASH>
 *  0x01 => <HEX>
 *  text1234text => text<NUM>text
 **/
fun generifyErrorMessage(originalMessage: String): String {
  return originalMessage
    .generifyFileNames()
    .generifyID()
    .generifyHash()
    .generifyHexadecimal()
    .generifyHexCode()
    .generifyNumber()
}

fun String.generifyFileNames(): String {
  val regex = "([A-Z]:)?([/\\\\][ a-zA-Z0-9_.-]+)+".toRegex()
  return replace(regex,"<FILE>")
}

/** text@3ba5aac, text => text<ID>, text */
fun String.generifyID(omitDollarSign: Boolean = false): String {
  val regexpStr = if (omitDollarSign) "[@#][A-Za-z\\d-_]+"
  else "[\$@#][A-Za-z\\d-_]+"

  return this.replace(regexpStr.toRegex(), "<ID>")
}

/** some-text.db451f59 => some-text.<HASH> */
fun String.generifyHash(): String = this
  .replace("[.]([A-Za-z]+\\d|\\d+[A-Za-z])[A-Za-z\\d]*".toRegex(), ".<HASH>")

/**
 * Replaces hexadecimal numbers in a string with a generic placeholder <NUM>.
 * The hexadecimal number should be either at the beginning/end of the string or surrounded by special characters.
 * Examples:
 * ```
 * sometext-aed23 => sometext-<NUM>
 * abc323#sometext => <NUM>#sometext
 * sometext$ABCDE2323$sometext => sometext$<NUM>$sometext
 * ```
 */
fun String.generifyHexadecimal(): String {
  val hexNumber ="[a-fA-F0-9]{2,}"
  val specialChars = "[\\W_]"
  val regex = Regex("($specialChars)($hexNumber)$|^($hexNumber)($specialChars)|($specialChars)($hexNumber)($specialChars)")
  return this.replace(regex) {
    if (it.groupValues[2].isNotEmpty()) {
      "${it.groupValues[1]}<NUM>"
    } else if (it.groupValues[4].isNotEmpty()) {
      "<NUM>${it.groupValues[4]}"
    } else {
      "${it.groupValues[5]}<NUM>${it.groupValues[7]}"
    }
  }
}

/** 0x01 => <HEX> */
fun String.generifyHexCode(): String = this.replace("0x[\\da-fA-F]+".toRegex(), "<HEX>")

/** text1234text => text<NUM>text */
fun String.generifyNumber(): String = this.replace("\\d+".toRegex(), "<NUM>")

fun String.generifyDollarSign(): String = this.replace("\\$<NUM>+".toRegex(), "")

/** Leave only numbers and characters */
fun String.replaceSpecialCharacters(newValue: String, vararg ignoreSymbols: String): String {
  val regex = Regex("[^a-zA-Z0-9${ignoreSymbols.joinToString(separator = "")}]")

  return this
    .replace(regex, newValue)
}

/** Leave only numbers and characters.
 * Replace everything else with hyphens.
 */
fun String.replaceSpecialCharactersWithHyphens(ignoreSymbols: List<String> = listOf(".", "/", """\\""")): String {
  return this
    .replaceSpecialCharacters(newValue = "-", *ignoreSymbols.toTypedArray())
    .replace("[-]+".toRegex(), "-")
    .removePrefix("-")
    .removeSuffix("-")
}
