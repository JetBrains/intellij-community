// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import com.intellij.platform.eel.EelApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Accept as an argument of your test, be sure to use [EelSource]
 * See [TestApplicationWithEel]
 */
@TestOnly
@ApiStatus.NonExtendable
interface EelHolder {
  val eel: EelApi
}