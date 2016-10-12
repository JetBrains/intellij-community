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
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.BuildRange
import com.intellij.openapi.util.SystemInfo
import org.jdom.Element
import org.jdom.JDOMException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class UpdatesInfo(node: Element) {
  private val products = node.getChildren("product").map { Product(it) }
  operator fun get(code: String): Product? = products.find { code in it.codes }
}

class Product(node: Element) {
  val name: String = node.getAttributeValue("name") ?: throw JDOMException("product@name missing")
  val codes: Set<String> = node.getChildren("code").map { it.value.trim() }.toSet()
  val channels: List<UpdateChannel> = node.getChildren("channel").map { UpdateChannel(it) }

  override fun toString() = codes.firstOrNull() ?: "-"
}

class UpdateChannel(node: Element) {
  companion object {
    const val LICENSING_EAP = "eap"
    const val LICENSING_PRODUCTION = "production"
  }

  val id: String = node.getAttributeValue("id") ?: throw JDOMException("channel@id missing")
  val status: ChannelStatus = ChannelStatus.fromCode(node.getAttributeValue("status"))
  val licensing: String = node.getAttributeValue("licensing", LICENSING_PRODUCTION)
  val homePageUrl: String? = node.getAttributeValue("url")
  val evalDays: Int = node.getAttributeValue("evalDays")?.toInt() ?: 30
  val builds: List<BuildInfo> = node.getChildren("build").map { BuildInfo(it) }

  override fun toString() = id
}

class BuildInfo(node: Element) {
  val number: BuildNumber = BuildNumber.fromString(node.getAttributeValue("fullNumber") ?: node.getAttributeValue("number") ?: throw JDOMException("build@number missing"))
  val apiVersion: BuildNumber = BuildNumber.fromString(node.getAttributeValue("apiVersion"), number.productCode) ?: number
  val version: String = node.getAttributeValue("version") ?: ""
  val message: String = node.getChild("message")?.value ?: ""
  val releaseDate: Date? = parseDate(node.getAttributeValue("releaseDate"))
  val target: BuildRange? = BuildRange.fromStrings(node.getAttributeValue("targetSince"), node.getAttributeValue("targetUntil"))
  val buttons: List<ButtonInfo> = node.getChildren("button").map { ButtonInfo(it) }
  val patches: List<PatchInfo> = node.getChildren("patch").map { PatchInfo(it) }

  private fun parseDate(value: String?): Date? = value?.let {
    try {
      SimpleDateFormat("yyyyMMdd", Locale.US).parse(it)  // same as the 'majorReleaseDate' in ApplicationInfo.xml
    }
    catch (e: ParseException) {
      Logger.getInstance(BuildInfo::class.java).info("Failed to parse build release date " + it)
      null
    }
  }

  override fun toString() = "${number}/${version}"
}

class ButtonInfo(node: Element) {
  val name: String = node.getAttributeValue("name") ?: throw JDOMException("button@name missing")
  val url: String = node.getAttributeValue("url") ?: throw JDOMException("button@url missing")
  val isDownload: Boolean = node.getAttributeValue("download") != null  // a button marked with this attribute is hidden when a patch is available

  override fun toString() = name
}

class PatchInfo(node: Element) {
  val fromBuild: BuildNumber = BuildNumber.fromString(node.getAttributeValue("fullFrom") ?: node.getAttributeValue("from") ?: throw JDOMException("patch@from missing"))
  val size: String? = node.getAttributeValue("size")
  val isAvailable: Boolean = node.getAttributeValue("exclusions")?.splitToSequence(",")?.none { it.trim() == osSuffix } ?: true

  val osSuffix: String
    get() = if (SystemInfo.isWindows) "win" else if (SystemInfo.isMac) "mac" else if (SystemInfo.isUnix) "unix" else "unknown"
}