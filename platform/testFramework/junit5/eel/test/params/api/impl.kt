// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import com.intellij.execution.target.EelTargetEnvironmentRequest
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.testFramework.junit5.eel.params.impl.junit5.EelInterceptor
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider
import com.intellij.testFramework.junit5.eel.EelFixtureFilter
import org.jetbrains.annotations.TestOnly


@TestOnly
internal class EelHolderImpl<T : Annotation>(val eelTestProvider: EelIjentTestProvider<T>, val annotation: T?) : EelHolder {
  override lateinit var eel: IjentApi
  override lateinit var type: EelType
  override lateinit var target: TargetEnvironmentConfiguration

  override fun toString(): String = buildString {
    append(eelTestProvider.name)
    annotation?.let { annotation ->
      append("(configured[mandatory=${eelTestProvider.isMandatory(annotation)}], ")
      append(eelTestProvider.annotationToUserVisibleString(annotation))
      append(")")
    }
  }

  override fun isEnabled(filter: EelFixtureFilter): Boolean {
    return when (type) {
      EelType.Wsl -> filter.isWslEnabled
      EelType.Docker -> filter.isDockerEnabled
      EelType.Local -> {
        logger<EelInterceptor>().warn("This code should not be reached")
        false
      }
    }
  }
}

@TestOnly
internal object LocalEelHolder : EelHolder {
  override val target: TargetEnvironmentConfiguration = EelTargetEnvironmentRequest.Configuration(eel)
  override val type: EelType = EelType.Local
  override val eel: EelApi get() = localEel
  override fun toString(): String = "Local"

  override fun isEnabled(filter: EelFixtureFilter): Boolean =
    filter.isLocalEelEnabled
}