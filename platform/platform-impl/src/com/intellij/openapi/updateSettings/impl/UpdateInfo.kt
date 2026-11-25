// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UpdateData")
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.BuildRange
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.system.CpuArch
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.NativeAccess
import com.intellij.util.system.OS
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Throws(IOException::class, JDOMException::class)
fun parseUpdateData(
  text: String,
  productCode: String = ApplicationInfo.getInstance().build.productCode,
): Product? = parseUpdateData(JDOMUtil.load(text), productCode)

fun parseUpdateData(node: Element, productCode: String = ApplicationInfo.getInstance().build.productCode): Product? =
  parseUpdateData(node, productCode, PatchInfo.OS_SUFFIX)

/**
 * Parses the update data for the OS [distributionOs]: a `<distribution>` element gives the file of this OS.
 * The value is an `os` value of the element, for example `mac`. A test gives it, because its machine has another OS.
 */
@Internal
fun parseUpdateData(node: Element, productCode: String, distributionOs: String): Product? =
  node.getChildren("product")
    .find { it.getChildren("code").any { code -> code.value.trim() == productCode } }
    ?.let { Product(it, productCode, distributionOs) }

class Product internal constructor(node: Element, private val productCode: String, distributionOs: String) {
  val name: @NlsSafe String = node.getMandatoryAttributeValue("name")
  val channels: List<UpdateChannel> = node.getChildren("channel").map { UpdateChannel(it, productCode, distributionOs) }
  val disableMachineId: Boolean = node.getAttributeValue("disableMachineId", "false") == "true"

  override fun toString(): String = productCode
}

class UpdateChannel @Internal constructor(node: Element, productCode: String, distributionOs: String) {
  @Internal
  constructor(node: Element, productCode: String) : this(node, productCode, PatchInfo.OS_SUFFIX)

  enum class Licensing { EAP, RELEASE; }

  val id: String = node.getMandatoryAttributeValue("id")
  val status: ChannelStatus = ChannelStatus.fromCode(node.getAttributeValue("status"))
  val licensing: Licensing = if (node.getAttributeValue("licensing") == "eap") Licensing.EAP else Licensing.RELEASE
  val evalDays: Int = node.getAttributeValue("evalDays")?.toInt() ?: 30
  val url: String? = node.getAttributeValue("url")
  val builds: List<BuildInfo> = node.getChildren("build").map { BuildInfo(it, productCode, distributionOs) }

  override fun toString(): String = id
}

class BuildInfo internal constructor(node: Element, productCode: String, distributionOs: String) {
  val number: BuildNumber = parseBuildNumber(node.getMandatoryAttributeValue("fullNumber", "number"), productCode)
  val apiVersion: BuildNumber = node.getAttributeValue("apiVersion")?.let { BuildNumber.fromStringWithProductCode(it, number.productCode) } ?: number
  val version: String = node.getAttributeValue("version") ?: ""
  val message: @NlsSafe String = node.getChild("message")?.value ?: ""
  val blogPost: String? = node.getChild("blogPost")?.getAttributeValue("url")
  val releaseDate: Date? = parseDate(node.getAttributeValue("releaseDate"))
  val target: BuildRange? = BuildRange.fromStrings(node.getAttributeValue("targetSince"), node.getAttributeValue("targetUntil"))
  val patches: List<PatchInfo> = node.getChildren("patch").map(::PatchInfo)
  val downloadUrl: String? = node.getChildren("button").find { it.getAttributeValue("download") != null }?.getMandatoryAttributeValue("url")
  @Internal
  val distribution: DistributionInfo? = node.getChild("distribution")?.let { parseDistribution(it, distributionOs) }

  private fun parseBuildNumber(value: String, productCode: String): BuildNumber {
    val buildNumber = BuildNumber.fromString(value)!!
    return if (buildNumber.productCode.isNotEmpty()) buildNumber else BuildNumber(productCode, *buildNumber.components)
  }

  private fun parseDate(value: String?): Date? =
    if (value == null) null
    else try {
      SimpleDateFormat("yyyyMMdd", Locale.US).parse(value)  // same as the 'majorReleaseDate' in ApplicationInfo.xml
    }
    catch (_: ParseException) {
      logger<BuildInfo>().info("invalid build release date: ${value}")
      null
    }

  override fun toString(): String = "${number}/${version}"
}

@Internal
class PatchInfo internal constructor(node: Element) {
  companion object {
    val OS_SUFFIX: String = if (SystemInfoRt.isWindows) "win" else if (SystemInfoRt.isMac) "mac" else if (SystemInfo.isUnix) "unix" else "unknown"
  }

  val fromBuild: BuildNumber = BuildNumber.fromString(node.getMandatoryAttributeValue("fullFrom", "from"))!!
  val size: String? = node.getAttributeValue("size")
  val isAvailable: Boolean = node.getAttributeValue("exclusions")?.splitToSequence(",")?.none { it.trim() == OS_SUFFIX } ?: true
}

/**
 * The full distribution of another product that replaces the IDE.
 * The update offers it instead of a patch. [fileUrl] is the file for the current OS and CPU.
 */
@Internal
class DistributionInfo(val product: String, val manifestUrl: String, val fileUrl: String)

// the `os` values of the systems where the IDE can install and start the distribution: macOS and Windows
private val MIGRATION_OS = setOf("mac", "win")
private val ARCH_SUFFIX: String by lazy { distributionArchSuffix() }

/**
 * The `arch` value of the distribution file for this machine.
 * On Windows, it is the CPU of the machine and not the CPU of the JVM, because the installer for another CPU does not run.
 * [windowsNativeArch] gives the CPU of a Windows machine, or `null` when it is unknown.
 */
@Internal
fun distributionArchSuffix(
  os: OS = OS.CURRENT,
  jvmArch: CpuArch = CpuArch.CURRENT,
  windowsNativeArch: () -> CpuArch? = { NativeAccess.getInstance().windowsNativeArch },
): String {
  val arch = when (os) {
    OS.Windows -> windowsNativeArch() ?: jvmArch
    OS.macOS, OS.Linux, OS.FreeBSD, OS.OHOS, OS.Other -> jvmArch
  }
  return when (arch) {
    CpuArch.ARM64 -> "aarch64"
    CpuArch.X86_64 -> "x64"
    CpuArch.X86, CpuArch.ARM32, CpuArch.OTHER, CpuArch.UNKNOWN -> "unknown"
  }
}

/**
 * Returns `null` when [isMigrationDisabled] is true, when the element is not valid, when [os] is not macOS or Windows,
 * or when the element has no file for [os] and [arch].
 * The update then works as if the element did not exist.
 */
@Internal
fun parseDistribution(
  node: Element,
  os: String = PatchInfo.OS_SUFFIX,
  arch: String = ARCH_SUFFIX,
  isMigrationDisabled: Boolean = MigrationKillSwitch.isActive(),
): DistributionInfo? {
  fun invalid(reason: String): DistributionInfo? {
    logger<DistributionInfo>().info("ignored <distribution>: ${reason}")
    return null
  }

  if (isMigrationDisabled) return null
  if (os !in MIGRATION_OS) return invalid("the migration works only on macOS and Windows")
  val product = node.getAttributeValue("product")?.takeIf { it.isNotBlank() } ?: return invalid("no 'product'")
  val manifestUrl = node.getAttributeValue("manifest")?.takeIf { it.isNotBlank() } ?: return invalid("no 'manifest'")
  val fileUrl = node.getChildren("file")
                  .find { it.getAttributeValue("os") == os && it.getAttributeValue("arch") == arch }
                  ?.getAttributeValue("url")?.takeIf { it.isNotBlank() }
                ?: return invalid("no file for ${os}/${arch}")
  return DistributionInfo(product, manifestUrl, fileUrl)
}

private fun Element.getMandatoryAttributeValue(attribute: String) =
  getAttributeValue(attribute) ?: throw JDOMException("${name}@${attribute} missing")

private fun Element.getMandatoryAttributeValue(attribute: String, fallback: String) =
  getAttributeValue(attribute) ?: getMandatoryAttributeValue(fallback)
