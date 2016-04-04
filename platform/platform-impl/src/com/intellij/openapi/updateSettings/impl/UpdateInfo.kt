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

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import org.jdom.Element
import org.jdom.JDOMException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class UpdatesInfo(node: Element) {
  private val products = node.getChildren("product").map { Product(it) }

  val productsCount: Int
    get() = products.size

  fun getProduct(code: String): Product? = products.find { it.hasCode(code) }
}

class Product(node: Element) {
  val name: String = node.getAttributeValue("name") ?: throw JDOMException("product.name missing")
  val channels: List<UpdateChannel> = node.getChildren("channel").map { UpdateChannel(it) }
  private val codes = node.getChildren("code").map { it.value.trim() }.toSet()

  fun hasCode(code: String): Boolean = codes.contains(code)

  fun findUpdateChannelById(id: String): UpdateChannel? = channels.find { it.id == id }

  fun getAllChannelIds(): List<String> = channels.map { it.id }
}

class UpdateChannel(node: Element) {
  companion object {
    const val LICENSING_EAP = "eap"
    const val LICENSING_PRODUCTION = "production"
  }

  val id: String = node.getAttributeValue("id") ?: throw JDOMException("channel.id missing")
  val name: String = node.getAttributeValue("name") ?: throw JDOMException("channel.name missing")
  val status: ChannelStatus = ChannelStatus.fromCode(node.getAttributeValue("status"))
  val licensing: String = node.getAttributeValue("licensing", LICENSING_PRODUCTION)
  val majorVersion: Int = node.getAttributeValue("majorVersion")?.toInt() ?: -1
  val homePageUrl: String? = node.getAttributeValue("url")
  val feedbackUrl: String? = node.getAttributeValue("feedback")
  val evalDays: Int = node.getAttributeValue("evalDays")?.toInt() ?: 30
  private val builds = node.getChildren("build").map { BuildInfo(it) }

  fun getLatestBuild(): BuildInfo? = latestBuild(builds)
  fun getLatestBuild(baseline: Int): BuildInfo? = latestBuild(builds.filter { it.number.baselineVersion == baseline })

  private fun latestBuild(builds: List<BuildInfo>) =
      builds.fold(null as BuildInfo?) { best, candidate -> if (best == null || best.compareTo(candidate) < 0) candidate else best }
}

class BuildInfo(node: Element) : Comparable<BuildInfo> {
  val number: BuildNumber = BuildNumber.fromString(node.getAttributeValue("number") ?: throw JDOMException("build.number missing"))
  val apiVersion: BuildNumber = node.getAttributeValue("apiVersion")?.let { BuildNumber.fromString(it, number.productCode) } ?: number
  val version: String = node.getAttributeValue("version") ?: ""
  val message: String = node.getChild("message")?.value ?: ""
  val releaseDate: Date? = node.getAttributeValue("releaseDate")?.let {
    try { SimpleDateFormat("yyyyMMdd", Locale.US).parse(it) }  // same as the 'majorReleaseDate' in ApplicationInfo.xml
    catch (e: ParseException) {
      Logger.getInstance(BuildInfo::class.java).info("Failed to parse build release date " + it)
      null
    }
  }
  val buttons: List<ButtonInfo> = node.getChildren("button").map { ButtonInfo(it) }
  private val patches = node.getChildren("patch").map { PatchInfo(it) }

  /**
   * Returns -1 if version information is missing or does not match to expected format "majorVer.minorVer"
   */
  val majorVersion: Int
    get() {
      val dotIndex = version.indexOf('.')
      if (dotIndex > 0) {
        try {
          return version.substring(0, dotIndex).toInt()
        }
        catch (ignored: NumberFormatException) { }
      }

      return -1
    }

  fun findPatchForCurrentBuild(): PatchInfo? = findPatchForBuild(ApplicationInfo.getInstance().build)

  fun findPatchForBuild(currentBuild: BuildNumber): PatchInfo? =
      patches.find { it.isAvailable && it.fromBuild.asStringWithoutProductCode() == currentBuild.asStringWithoutProductCode() }

  override fun compareTo(other: BuildInfo): Int = number.compareTo(other.number)

  override fun toString(): String = "BuildInfo(number=$number)"
}

class ButtonInfo(node: Element) {
  val name: String = node.getAttributeValue("name") ?: throw JDOMException("button.name missing")
  val url: String = node.getAttributeValue("url") ?: throw JDOMException("button.url missing")
  val isDownload: Boolean = node.getAttributeValue("download") != null  // a button marked with this attribute is hidden when a patch is available
}

class PatchInfo(node: Element) {
  val fromBuild: BuildNumber = BuildNumber.fromString(node.getAttributeValue("from") ?: throw JDOMException("patch.from missing"))
  val size: String? = node.getAttributeValue("size")
  val isAvailable: Boolean = node.getAttributeValue("exclusions")?.split(",")?.none { it.trim() == osSuffix } ?: true

  val osSuffix: String
    get() = if (SystemInfo.isWindows) "win" else if (SystemInfo.isMac) "mac" else if (SystemInfo.isUnix) "unix" else "unknown"
}