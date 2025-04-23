// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.impl.junit5

import com.intellij.platform.eel.EelApi
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelTestProvider
import org.jetbrains.annotations.TestOnly

@TestOnly
internal class EelHolderImpl(val eelTestProvider: EelTestProvider<*>) : EelHolder {
  override lateinit var eel: EelApi

  override fun toString(): String = eelTestProvider.toString()
}