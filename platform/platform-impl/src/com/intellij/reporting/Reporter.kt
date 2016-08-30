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

import com.google.gson.Gson
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.diagnostic.Logger
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType


private class StatsServerInfo(var status: String, var url: String) {
  fun isServiceAlive() = "ok" == status
}

private object Utils {
  val gson = Gson()
}


object StatsSender {
  private val infoUrl = "https://www.jetbrains.com/config/features-service-status.json"
  private val LOG = Logger.getInstance(StatsSender::class.java)
  
  private fun requestServerUrl(): String? {
    try {
      val response = Request.Get(infoUrl).execute().returnContent().asString()
      val info = Utils.gson.fromJson(response, StatsServerInfo::class.java)
      if (info.isServiceAlive()) return info.url
    }
    catch (e: Exception) {
      LOG.debug(e)
    }

    return null
  }
  
  fun send(text: String): Boolean {
    val url = requestServerUrl() ?: return false
    try {
      val response = Request.Post(url).bodyString(text, ContentType.TEXT_HTML).execute()
      val code = response.handleResponse { it.statusLine.statusCode }
      if (code >= 200 && code < 300) {
        return true
      }
    }
    catch (e: Exception) {
      LOG.debug(e)
    }
    return false
  }
  
}

fun <T> createReportLine(recorderId: String, data: T): String {
  val json = Utils.gson.toJson(data)
  val userUid = PermanentInstallationID.get()
  val stamp = System.currentTimeMillis()
  return "$stamp\t$recorderId\t$userUid\trandom_session_id\t$json"
}