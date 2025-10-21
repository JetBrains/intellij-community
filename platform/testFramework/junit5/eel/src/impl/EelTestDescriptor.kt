// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPathBoundDescriptor
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

internal class EelTestDescriptor(override val rootPath: Path, val id: String, override val osFamily: EelOsFamily, val apiProvider: () -> EelApi) : EelPathBoundDescriptor {
  override val machine: EelMachine = object : EelMachine {
    override val name: @NonNls String = "mock $id"
    override val osFamily: EelOsFamily get() = this@EelTestDescriptor.osFamily
    override suspend fun toEelApi(descriptor: EelDescriptor): EelApi = apiProvider()
  }

  override suspend fun toEelApi(): EelApi {
    return apiProvider()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as EelTestDescriptor

    if (rootPath != other.rootPath) return false
    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    var result = rootPath.hashCode()
    result = 31 * result + id.hashCode()
    return result
  }
}