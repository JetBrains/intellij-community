// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import org.gradle.internal.jvm.inspection.JvmVendor

class GradleDaemonJvmCriteria(
  val version: String?,
  val vendor: JvmVendor?,
) {

  fun matches(matcher: GradleDaemonJvmCriteria): Boolean {
    val isVersionMatched = matcher.version == null || matcher.version == version
    val isVendorMatched = matcher.vendor == null || matcher.vendor.rawVendor == vendor?.rawVendor
    return isVersionMatched && isVendorMatched
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GradleDaemonJvmCriteria) return false

    if (version != other.version) return false
    if (vendor?.rawVendor != other.vendor?.rawVendor) return false

    return true
  }

  override fun hashCode(): Int {
    var result = version?.hashCode() ?: 0
    result = 31 * result + (vendor?.rawVendor?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String {
    return "GradleDaemonJvmCriteria(version=${version}, vendor=${vendor?.rawVendor})"
  }

  companion object {
    val ANY = GradleDaemonJvmCriteria(null, null)
  }
}
