// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.util

import java.security.MessageDigest


object FileTextUtil {
  fun computeChecksum(text: String): String {
    val sha = MessageDigest.getInstance("SHA-256")
    val digest = sha.digest(text.toByteArray())
    return digest.fold("", { str, it -> str + "%02x".format(it) })
  }

  fun getDiff(text1: String, text2: String): String {
    if (text1.lines().size != text2.lines().size) {
      return "Number of lines differ"
    }
    val lines = text1.lines().zip(text2.lines())
    val sb = StringBuilder()
    for (pair in lines) {
      if (pair.first != pair.second) {
        sb.appendLine("[${pair.first}] => [${pair.second}]")
      }
    }
    return sb.toString()
  }
}