// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPathBoundDescriptor
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

/**
 * [rootPath] is parsed on the first read. The fixture registers the routing backend of the fake root after it creates
 * the descriptor, so a later parse gives a path of the fake file system.
 */
internal class EelTestDescriptor(private val fakeRoot: String, val id: String, override val osFamily: EelOsFamily) : EelPathBoundDescriptor {
  override val rootPath: Path by lazy { Path.of(fakeRoot) }

  override val name: @NonNls String = "mock $id"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as EelTestDescriptor

    if (fakeRoot != other.fakeRoot) return false
    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    var result = fakeRoot.hashCode()
    result = 31 * result + id.hashCode()
    return result
  }
}
