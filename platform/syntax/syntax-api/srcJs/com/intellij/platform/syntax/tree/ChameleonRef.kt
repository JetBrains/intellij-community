// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.tree

import fleet.util.multiplatform.Actual

private class ChameleonRefImpl(private var ref: AstMarkersChameleon? = null) : ChameleonRef {
  override val value: AstMarkersChameleon?
    get() = ref

  override fun set(value: AstMarkersChameleon) {
    ref = value
  }

  override fun realize(func: () -> AstMarkersChameleon): AstMarkersChameleon =
    ref ?: func().also { ref = it }

  override fun toString(): String = ref?.toString() ?: "null"
}

/**
 * JS implementation of [newChameleonRef].
 */
@Suppress("unused")
@Actual("ChameleonRef")
internal fun newChameleonRefJs(): ChameleonRef = ChameleonRefImpl()

/**
 * JS implementation of [newChameleonRef].
 */
@Suppress("unused")
@Actual("ChameleonRef")
internal fun newChameleonRefJs(chameleon: AstMarkersChameleon): ChameleonRef = ChameleonRefImpl(chameleon)