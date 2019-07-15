// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.jna

import com.sun.jna.Memory

class DisposableMemory(size: Long) : Memory(size) {
  @Synchronized public override fun dispose() {
    super.dispose()
  }
}