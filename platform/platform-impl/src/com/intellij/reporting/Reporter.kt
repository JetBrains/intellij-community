// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.reporting

import com.google.common.net.HttpHeaders
import com.google.gson.Gson
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.GZIPOutputStream

private class StatsServerInfo(@JvmField var status: String,
                              @JvmField var url: String,
                              @JvmField var urlForZipBase64Content: String) {
  fun isServiceAlive() = "ok" == status
}

private val gson by lazy { Gson() }

object StatsSender {
  private const val infoUrl = "https://www.jetbrains.com/config/features-service-status.json"
  private val LOG = Logger.getInstance(StatsSender::class.java)

  private fun requestServerUrl(): StatsServerInfo? {
    try {
      val info = gson.fromJson(HttpRequests.request(infoUrl).readString(), StatsServerInfo::class.java)
      if (info.isServiceAlive()) return info
    }
    catch (e: Exception) {
      LOG.debug(e)
    }

    return null
  }

  fun send(text: String, compress: Boolean = true): Boolean {
    val info = requestServerUrl() ?: return false
    try {
      executeRequest(info, text, compress)
      return true
    }
    catch (e: Exception) {
      LOG.debug(e)
    }
    return false
  }

  private fun executeRequest(info: StatsServerInfo, text: String, compress: Boolean) {
    if (compress) {
      val data = compressBase64Gzip(text)
      HttpRequests
        .post(info.urlForZipBase64Content, null)
        .tuner { it.setRequestProperty(HttpHeaders.CONTENT_ENCODING, "gzip") }
        .write(data)
      return
    }

    HttpRequests.post(info.url, "text/html").write(text)
  }
}

private fun compressBase64Gzip(text: String) = compressBase64Gzip(text.toByteArray())

fun compressBase64Gzip(data: ByteArray): ByteArray {
  val outputStream = ByteArrayOutputStream()
  GZIPOutputStream(outputStream).use {
    it.write(data)
  }
  return Base64.getEncoder().encode(outputStream.toByteArray())
}

fun <T> createReportLine(recorderId: String, sessionId: String, data: T): String {
  val json = gson.toJson(data)
  val userUid = PermanentInstallationID.get()
  val stamp = System.currentTimeMillis()
  return "$stamp\t$recorderId\t$userUid\t$sessionId\t$json"
}