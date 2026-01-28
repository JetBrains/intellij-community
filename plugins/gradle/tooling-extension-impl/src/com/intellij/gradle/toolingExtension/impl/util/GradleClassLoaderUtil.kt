// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util

import com.intellij.gradle.toolingExtension.GradleToolingExtensionProperties.isResilientModelFetchApiUsed
import com.intellij.gradle.toolingExtension.impl.model.util.daemonClassLoaderModel.GradleDaemonClassLoaderModel
import com.intellij.gradle.toolingExtension.impl.model.utilDummyModel.DummyModel
import com.intellij.gradle.toolingExtension.impl.telemetry.GradleOpenTelemetry
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter

internal object GradleClassLoaderUtil {

  @JvmStatic
  fun GradleModelController.getDaemonClassLoader(): ClassLoader {
    val dummyModelClass = when (isResilientModelFetchApiUsed()) {
      true -> GradleDaemonClassLoaderModel::class.java
      else -> DummyModel::class.java
    }
    val dummyModel = GradleOpenTelemetry.callWithSpan("GetDaemonClassLoaderModel") {
      fetchModel(dummyModelClass)
    }
    return ProtocolToModelAdapter().unpack(dummyModel).javaClass.classLoader
  }
}