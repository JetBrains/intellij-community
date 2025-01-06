// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.pathOs

internal class EelTestDescriptor(val id: String, os: EelPath.OS, val apiProvider: () -> EelApi) : EelDescriptor {
  override val operatingSystem: EelPath.OS = os

  override suspend fun upgrade(): EelApi {
    return apiProvider()
  }

  override fun equals(other: Any?): Boolean {
    return other is EelTestDescriptor && other.id == id
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + apiProvider.hashCode()
    return result
  }
}