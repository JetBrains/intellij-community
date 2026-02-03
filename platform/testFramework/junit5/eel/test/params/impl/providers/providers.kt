// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.impl.providers

import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider
import org.jetbrains.annotations.TestOnly
import java.util.*

@TestOnly
internal fun getIjentTestProviders(): List<EelIjentTestProvider<*>> =
  ServiceLoader.load(EelIjentTestProvider::class.java).toList()