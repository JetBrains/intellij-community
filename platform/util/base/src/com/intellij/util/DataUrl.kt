// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import java.util.Base64

private const val DATA_URL_PREFIX = "data:"
private const val DATA_URL_BASE64_PARAM = "base64"
private const val DATA_URL_DEFAULT_MEDIATYPE = "text/plain;charset=US-ASCII"

data class DataUrl(val data: ByteArray, val contentType: String, val params: List<String>) {
  companion object {
    fun isDataUrl(s: String) = s.startsWith(DATA_URL_PREFIX)
    fun parse(dataUrl: String) = parseDataUrl(dataUrl)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DataUrl

    if (!data.contentEquals(other.data)) return false
    if (contentType != other.contentType) return false
    if (params != other.params) return false

    return true
  }

  override fun hashCode(): Int {
    var result = data.contentHashCode()
    result = 31 * result + contentType.hashCode()
    result = 31 * result + params.hashCode()
    return result
  }

  fun toString(includeClassName: Boolean, stripContent: Boolean): String {
    val sb = StringBuilder().append(contentType)

    if (includeClassName) {
      sb.append("DataUrl(")
    }

    if (params.isNotEmpty()) {
      sb.append(";").append(params.joinToString(";"))
    }

    if (stripContent) {
      sb.append(",[length=").append(data.size).append("]")
    } else {
      sb.append(";base64,").append(Base64.getEncoder().encode(data))
    }

    if (includeClassName) {
      sb.append(")")
    }

    return sb.toString()
  }

  override fun toString(): String {
    return toString(includeClassName = true, stripContent = true)
  }
}

/**
 * Parses data url as defined in https://www.ietf.org/rfc/rfc2397.html
 *
 *        dataurl    := "data:" [ mediatype ] [ ";base64" ] "," data
 *        mediatype  := [ type "/" subtype ] *( ";" parameter )
 *        data       := *urlchar
 *        parameter  := attribute "=" value
 */
internal fun parseDataUrl(url: String): DataUrl {
  if (!url.startsWith(DATA_URL_PREFIX)) throw IllegalArgumentException("Not a data url: '$url'")
  val dataSeparatorIdx = url.indexOf(',').takeIf { it >= 0 } ?: throw IllegalArgumentException("Invalid data url: '$url'")
  val mediaType = url.substring(DATA_URL_PREFIX.length, dataSeparatorIdx).takeIf { it.isNotBlank() } ?: DATA_URL_DEFAULT_MEDIATYPE

  val rawParams = mediaType.split(';')
  val contentType = rawParams[0]
  val isBase64 = rawParams.last() == DATA_URL_BASE64_PARAM
  val params = rawParams.subList(1, if (isBase64) rawParams.size - 1 else rawParams.size)
  val dataStr = url.substring(dataSeparatorIdx + 1)
  val data = if (isBase64) decodeBase64(dataStr) else decodeUrlEncoded(dataStr)
  return DataUrl(data, contentType, params)
}


const val ASCII_CODE_OF_0 = '0'.code
const val ASCII_CODE_OF_9 = '9'.code
const val ASCII_CODE_OF_A = 'A'.code
internal fun charToHex(c: Char): Int {
  var res = c.uppercaseChar().code - '0'.code;
  if (res > 9) {
    res -= ASCII_CODE_OF_A - ASCII_CODE_OF_9 - 1
  }
  if (res < 0 || res > 15) {
    throw IllegalArgumentException("Invalid hex char '$c'")
  }

  return res
}

internal fun decodeUrlEncoded(dataUrl: String): ByteArray {
  val tmpRes = ByteArray(dataUrl.length)
  var resPos = 0
  var dataPos = 0
  while (dataPos < dataUrl.length) {
    val cc = dataUrl.get(dataPos).code
    if (cc > 127) {
      throw IllegalArgumentException("Non-ASCII character in URL-encoded data at $dataPos: '$dataUrl'")
    }

    if (cc == '%'.code) {
      if (dataUrl.length - dataPos < 2) throw IllegalArgumentException("Incomplete '%xx' sequence at the end if data url '$dataUrl'")
      tmpRes[resPos++] = ((charToHex(dataUrl.get(dataPos + 1)) shl 4) or (charToHex(dataUrl.get(dataPos + 2)))).toByte()
      dataPos += 3
    } else {
      tmpRes[resPos++] = cc.toByte()
      dataPos++
    }
  }

  return if (resPos == tmpRes.size) tmpRes else tmpRes.copyOfRange(0, resPos)
}

internal fun decodeBase64(base64Str: String): ByteArray {
  return java.util.Base64.getDecoder().decode(base64Str)
}
