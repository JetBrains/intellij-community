// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * Simplifies test grouping by replacing numbers, hashes, hex numbers with predefined constant values <ID>, <HASH>, <HEX>, <UID>
 *  Eg:
 *  /mnt/build/temp35321324432 => <FILE>
 *  text@3ba5aac, text => text<ID>, text
 *  some-text.db451f59 => some-text.<HASH>
 *  0x01 => <HEX>
 *  text1234text => text<NUM>text
 *  id=l8nnaskjn7k16m0qqqpt => id=<UID>
 **/
fun generifyErrorMessage(originalMessage: String): String {
  return originalMessage
    .generifyFileNames()
    .generifyID()
    .generifyUID()
    .generifyHash()
    .generifyHexadecimal()
    .generifyHexCode()
    .generifyNumber()
    .generifyKernelMessage()
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

/**
 * Replaces a fleet.util.UID (random base-32, fixed length) with the placeholder <UID>.
 * Without this, [generifyNumber] only swaps the digit runs and leaves the random letters,
 * so each UID still generifies to a unique string. E.g. id=l8nnaskjn7k16m0qqqpt => id=<UID>
 *
 * Anchored to what UID.random() emits: alphabet [0-9a-v] (0123456789abcdefghijklmnopqrstuv),
 * length 20 (see UID.BASE32 / UID.LENGTH in fleet/util/UID.kt).
 */
fun String.generifyUID(): String {
  // boundary excludes the UID charset [A-Za-z0-9_-] so we don't match inside a longer token
  val regex = Regex("(?<![A-Za-z0-9_-])[0-9a-v]{20}(?![A-Za-z0-9_-])")
  return this.replace(regex) { match ->
    val token = match.value
    // require a mix of digits and letters, so pure numbers / pure words are left to other steps
    if (token.any { it.isDigit() } && token.any { it in 'a'..'v' }) "<UID>" else token
  }
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

fun String.generifyKernelMessage(): String {
  val regex = Regex("\\[Kernel.*, CoroutineName\\((.*)\\), .*]")
  return this.replace(regex) {
    "[<Kernel details> ${it.groupValues[1]}]"
  }
}


/** 0x01 => <HEX> */
fun String.generifyHexCode(): String = this.replace("0x[\\da-fA-F]+".toRegex(), "<HEX>")

/** text1234text => text<NUM>text */
fun String.generifyNumber(): String = this.replace("\\d+".toRegex(), "<NUM>")
