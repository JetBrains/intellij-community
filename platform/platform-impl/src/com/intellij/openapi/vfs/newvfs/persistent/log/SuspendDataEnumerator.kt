// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

interface SuspendDataEnumerator<D> {
  suspend fun enumerate(value: D?): Int

  suspend fun valueOf(ordinal: Int): D?
}