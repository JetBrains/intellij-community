// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.validation.impl

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.validation.Level
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
internal fun createValidationInfo(component: JComponent,
                                  @NlsContexts.DialogMessage message: String,
                                  level: Level = Level.ERROR): ValidationInfo {
  val result = ValidationInfo(message, component)
  if (level == Level.WARNING) {
    result.asWarning().withOKEnabled()
  }
  return result
}
