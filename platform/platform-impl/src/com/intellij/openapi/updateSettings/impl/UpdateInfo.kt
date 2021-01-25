// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.BuildRange
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import org.jdom.Element
import org.jdom.JDOMException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class UpdatesInfo(node: Element) {
  private val products = node.getChildren("product").map(::Product)
  operator fun get(code: String): Product? = products.find { code in it.codes }
}

class Product internal constructor(node: Element) {
  @NlsSafe val name: String = node.getMandatoryAttributeValue("name")
  val codes: Set<String> = node.getChildren("code").map { it.value.trim() }.toSet()
  val channels: List<UpdateChannel> = node.getChildren("channel").map(::UpdateChannel)

  override fun toString(): String = codes.firstOrNull() ?: "-"
}

class UpdateChannel internal constructor(node: Element) {
  enum class Licensing { EAP, RELEASE; }

  val id: String = node.getMandatoryAttributeValue("id")
  val status: ChannelStatus = ChannelStatus.fromCode(node.getAttributeValue("status"))
  val licensing: Licensing = if (node.getAttributeValue("licensing") == "eap") Licensing.EAP else Licensing.RELEASE
  val evalDays: Int = node.getAttributeValue("evalDays")?.toInt() ?: 30
  val url: String? = node.getAttributeValue("url")
  val builds: List<BuildInfo> = node.getChildren("build").map(::BuildInfo)

  override fun toString(): String = id
}

class BuildInfo internal constructor(node: Element) {
  val number: BuildNumber = parseBuildNumber(node.getMandatoryAttributeValue("fullNumber", "number"))
  val apiVersion: BuildNumber = node.getAttributeValue("apiVersion")?.let { BuildNumber.fromStringWithProductCode(it, number.productCode) } ?: number
  val version: String = node.getAttributeValue("version") ?: ""
  @NlsSafe val message: String = node.getChild("message")?.value ?: ""
  val blogPost: String? = node.getChild("blogPost")?.getAttributeValue("url")
  val releaseDate: Date? = parseDate(node.getAttributeValue("releaseDate"))
  val target: BuildRange? = BuildRange.fromStrings(node.getAttributeValue("targetSince"), node.getAttributeValue("targetUntil"))
  val patches: List<PatchInfo> = node.getChildren("patch").map(::PatchInfo)

  private val buttons: List<ButtonInfo> = node.getChildren("button").map(::ButtonInfo)

  private fun parseBuildNumber(value: String): BuildNumber {
    var buildNumber = BuildNumber.fromString(value)!!
    if (buildNumber.productCode.isEmpty()) {
      buildNumber = BuildNumber(ApplicationInfoImpl.getShadowInstance().build.productCode, *buildNumber.components)
    }
    return buildNumber
  }

  private fun parseDate(value: String?): Date? =
    if (value == null) null
    else try {
      SimpleDateFormat("yyyyMMdd", Locale.US).parse(value)  // same as the 'majorReleaseDate' in ApplicationInfo.xml
    }
    catch (e: ParseException) {
      Logger.getInstance(BuildInfo::class.java).info("invalid build release date: $value")
      null
    }

  val downloadUrl: String?
    get() = buttons.find(ButtonInfo::isDownload)?.url

  override fun toString(): String = "${number}/${version}"

  private class ButtonInfo constructor(node: Element) {
    // "name" is no longer used
    val url: String = node.getMandatoryAttributeValue("url")
    val isDownload: Boolean = node.getAttributeValue("download") != null
  }
}

class PatchInfo internal constructor(node: Element) {
  companion object {
    val OS_SUFFIX = if (SystemInfo.isWindows) "win" else if (SystemInfo.isMac) "mac" else if (SystemInfo.isUnix) "unix" else "unknown"
  }

  val fromBuild: BuildNumber = BuildNumber.fromString(node.getMandatoryAttributeValue("fullFrom", "from"))!!
  val size: String? = node.getAttributeValue("size")
  val isAvailable: Boolean = node.getAttributeValue("exclusions")?.splitToSequence(",")?.none { it.trim() == OS_SUFFIX } ?: true
}

private fun Element.getMandatoryAttributeValue(attribute: String) =
  getAttributeValue(attribute) ?: throw JDOMException("${name}@${attribute} missing")

private fun Element.getMandatoryAttributeValue(attribute: String, fallback: String) =
  getAttributeValue(attribute) ?: getMandatoryAttributeValue(fallback)
