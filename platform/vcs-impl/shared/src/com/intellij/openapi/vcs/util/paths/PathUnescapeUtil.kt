// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.util.paths

import org.jetbrains.annotations.ApiStatus
import java.nio.charset.Charset

@ApiStatus.Internal
object PathUnescapeUtil {
  /**
   * Unescapes a path in the C-style quoted form, as Git writes it.
   *
   * If the file name has quotes, Git escapes them and also encloses the file name in quotes: `"\"quote"`.
   *
   * If the file name has spaces, Git shows the name as is. It does not escape the spaces and does not add quotes.
   *
   * @param path a path to unescape
   * @return the unescaped path, which you can find in the VFS or in the file system
   * @throws IllegalArgumentException if the path is not valid
   */
  @JvmStatic
  fun unescape(path: String): String {
    val quote = "\""
    val unquotedPath = if (path.startsWith(quote) && path.endsWith(quote)) path.substring(1, path.length - 1) else path
    return decodeEscapes(unquotedPath)
  }

  private fun decodeEscapes(path: String): String {
    if (path.indexOf('\\') == -1) return path

    val encoding = Charset.defaultCharset()

    val l = path.length
    val rc = StringBuilder(l)
    var i = 0
    while (i < path.length) {
      val c = path[i]
      if (c == '\\') {
        i++
        if (i >= l) {
          throw IllegalArgumentException("Unterminated escape sequence in the path: $path")
        }
        when (val e = path[i]) {
          '\\' -> rc.append('\\')
          't' -> rc.append('\t')
          'n' -> rc.append('\n')
          'r' -> rc.append('\r')
          'a' -> rc.append('\u0007')
          'b' -> rc.append('\b')
          'f' -> rc.append('\u000c')
          '"' -> rc.append('"')
          else -> {
            if (isOctal(e)) {
              // collect sequence of characters as a byte array.
              // count bytes first
              var n = 0
              var j = i
              while (j < l) {
                if (isOctal(path[j])) {
                  n++
                  var k = 0
                  while (k < 3 && j < l && isOctal(path[j])) {
                    j++
                    k++
                  }
                }
                if (j + 1 >= l || path[j] != '\\' || !isOctal(path[j + 1])) {
                  break
                }
                j++
              }
              // convert to byte array
              val b = ByteArray(n)
              n = 0
              while (i < l) {
                if (isOctal(path[i])) {
                  var code = 0
                  var k = 0
                  while (k < 3 && i < l && isOctal(path[i])) {
                    code = code * 8 + (path[i] - '0')
                    i++
                    k++
                  }
                  b[n++] = code.toByte()
                }
                if (i + 1 >= l || path[i] != '\\' || !isOctal(path[i + 1])) {
                  break
                }
                i++
              }
              i--
              assert(n == b.size)
              // add them to string
              rc.append(String(b, encoding))
            }
            else {
              throw IllegalArgumentException("Unknown escape sequence '\\" + path[i] + "' in the path: " + path)
            }
          }
        }
      }
      else {
        rc.append(c)
      }
      i++
    }
    return rc.toString()
  }

  private fun isOctal(ch: Char): Boolean = ch in '0'..'7'
}
