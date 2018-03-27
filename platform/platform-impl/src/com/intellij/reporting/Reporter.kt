/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.reporting

import com.google.common.net.HttpHeaders
import com.google.gson.Gson
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.diagnostic.Logger
import org.apache.commons.codec.binary.Base64OutputStream
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import org.apache.http.message.BasicHeader
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream


private class StatsServerInfo(@JvmField var status: String,
                              @JvmField var url: String,
                              @JvmField var urlForZipBase64Content: String) {
  fun isServiceAlive() = "ok" == status
}

private object Utils {
  val gson = Gson()
}

object StatsSender {
  private val infoUrl = "https://www.jetbrains.com/config/features-service-status.json"
  private val LOG = Logger.getInstance(StatsSender::class.java)

  private fun requestServerUrl(): StatsServerInfo? {
    try {
      val response = Request.Get(infoUrl).execute().returnContent().asString()
      val info = Utils.gson.fromJson(response, StatsServerInfo::class.java)
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
      val response = createRequest(info, text, compress).execute()
      val code = response.handleResponse { it.statusLine.statusCode }
      if (code in 200..299) {
        return true
      }
    }
    catch (e: Exception) {
      LOG.debug(e)
    }
    return false
  }

  private fun createRequest(info: StatsServerInfo, text: String, compress: Boolean): Request {
    if (compress) {
      val data = Base64GzipCompressor.compress(text)
      val request = Request.Post(info.urlForZipBase64Content).bodyByteArray(data)
      request.addHeader(BasicHeader(HttpHeaders.CONTENT_ENCODING, "gzip"))
      return request
    }

    return Request.Post(info.url).bodyString(text, ContentType.TEXT_HTML)
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
  val json = Utils.gson.toJson(data)
  val userUid = PermanentInstallationID.get()
  val stamp = System.currentTimeMillis()
  return "$stamp\t$recorderId\t$userUid\t$sessionId\t$json"
}