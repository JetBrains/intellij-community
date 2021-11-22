// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd.util

import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.wm.ex.ProgressIndicatorEx

fun ProgressIndicatorEx.subscribeOnCancel(action: () -> Unit) {
  addStateDelegate(object : AbstractProgressIndicatorExBase() {
    override fun cancel() {
      super.cancel()
      action()
    }
  })
}