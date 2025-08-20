// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.tree

import fleet.util.multiplatform.Actual
import java.lang.ref.SoftReference

private class ChameleonRefImpl(
  @Volatile private var ref: SoftReference<AstMarkersChameleon>? = null,
) : ChameleonRef {
  override val value: AstMarkersChameleon?
    get() = ref?.get()

  override fun set(value: AstMarkersChameleon) {
    ref = SoftReference(value)
  }

  override fun realize(func: () -> AstMarkersChameleon): AstMarkersChameleon =
    ref?.get() ?: synchronized(this) {
      ref?.get() ?: func().also { ref = SoftReference(it) }
    }

  override fun toString(): String = ref?.toString() ?: "null"
}

/**
 * Jvm implementation of [newChameleonRef].
 */
@Suppress("unused")
@Actual("ChameleonRef")
internal fun newChameleonRefJvm(): ChameleonRef = ChameleonRefImpl()

/**
 * Jvm implementation of [newChameleonRef].
 */
@Suppress("unused")
@Actual("ChameleonRef")
internal fun newChameleonRefJvm(chameleon: AstMarkersChameleon): ChameleonRef = ChameleonRefImpl(SoftReference(chameleon))