// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.reporting

import com.google.common.net.HttpHeaders
import com.google.gson.Gson
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import org.apache.commons.codec.binary.Base64OutputStream
import java.io.ByteArrayOutputStream
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
      val data = Base64GzipCompressor.compress(text)
      HttpRequests
        .post(info.urlForZipBase64Content, null)
        .tuner { it.setRequestProperty(HttpHeaders.CONTENT_ENCODING, "gzip") }
        .write(data)
      return
    }

    HttpRequests.post(info.url, "text/html").write(text)
  }
}

private object Base64GzipCompressor {
  fun compress(text: String): ByteArray {
    val outputStream = ByteArrayOutputStream()
    val base64Stream = GZIPOutputStream(Base64OutputStream(outputStream))
    base64Stream.write(text.toByteArray())
    base64Stream.close()
    return outputStream.toByteArray()
  }
}

fun <T> createReportLine(recorderId: String, sessionId: String, data: T): String {
  val json = gson.toJson(data)
  val userUid = PermanentInstallationID.get()
  val stamp = System.currentTimeMillis()
  return "$stamp\t$recorderId\t$userUid\t$sessionId\t$json"
}