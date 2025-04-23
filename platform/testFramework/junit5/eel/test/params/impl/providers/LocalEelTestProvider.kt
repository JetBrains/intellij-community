// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.impl.providers

import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelTestProvider
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelTestProvider.StartResult
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KClass

@TestOnly
internal object LocalEelTestProvider : EelTestProvider<Unit> {
  override fun toString(): String = "Local"
  override val mandatoryAnnotationClass: KClass<Unit>? = null

  override suspend fun start(scope: CoroutineScope, annotation: Unit?): StartResult =
    StartResult.Started(eel = localEel)
}