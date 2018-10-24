// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.progress.EmptyProgressIndicator

class NonReusableEmptyProgressIndicator : EmptyProgressIndicator() {
  override fun start() {
    checkCanceled()
    super.start()
  }
}