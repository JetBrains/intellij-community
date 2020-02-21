// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.init

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator

private class GrazieIDEInit : PreloadingActivity() {
  override fun preload(indicator: ProgressIndicator) {
    GrazieStateLifecycle.publisher.init(GrazieConfig.get())
  }
}

