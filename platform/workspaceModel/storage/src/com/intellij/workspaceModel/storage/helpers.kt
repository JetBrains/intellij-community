// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.openapi.diagnostic.Logger

inline fun Logger.assert(value: Boolean, lazyMessage: () -> String): Boolean {
  return value || this.assertTrue(false, lazyMessage())
}