// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider
import org.jetbrains.annotations.TestOnly


@TestOnly
internal class EelHolderImpl<T : Annotation>(val eelTestProvider: EelIjentTestProvider<T>, val annotation: T?) : EelHolder {
  override lateinit var eel: IjentApi
  override var target: TargetEnvironmentConfiguration? = null
  override val type: EelType = eelTestProvider.eelType

  override fun toString(): String = buildString {
    append(eelTestProvider.name)
    annotation?.let { annotation ->
      append("(configured[mandatory=${eelTestProvider.isMandatory(annotation)}], ")
      append(eelTestProvider.annotationToUserVisibleString(annotation))
      append(")")
    }
  }
}

@TestOnly
internal object LocalEelHolder : EelHolder {
  override val type: EelType = EelType.LOCAL
  override val eel: EelApi get() = localEel
  override fun toString(): String = "Local"
  override val target: TargetEnvironmentConfiguration? = null
}