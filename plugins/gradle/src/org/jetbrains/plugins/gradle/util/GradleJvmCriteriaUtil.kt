// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleJvmCriteriaUtil")
@file:ApiStatus.Internal

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria

fun JdkVersionDetector.JdkVersionInfo.toJvmCriteria(): GradleDaemonJvmCriteria {
  return GradleDaemonJvmCriteria(
    version = version.feature.toString(),
    vendor = variant.toJvmVendor()
  )
}

fun JdkItem.toJvmCriteria(): GradleDaemonJvmCriteria {
  return GradleDaemonJvmCriteria(
    version = jdkMajorVersion.toString(),
    vendor = product.toJvmVendor()
  )
}