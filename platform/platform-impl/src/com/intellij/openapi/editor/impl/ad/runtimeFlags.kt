// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import com.intellij.openapi.application.isRhizomeAdEnabled


val isRhizomeAdRebornEnabled: Boolean
  get() {
    if (isRhizomeAdEnabled) {
      return true
    }
    return System.getProperty("ijpl.rhizome.ad2.enabled", "false").toBoolean()
  }
