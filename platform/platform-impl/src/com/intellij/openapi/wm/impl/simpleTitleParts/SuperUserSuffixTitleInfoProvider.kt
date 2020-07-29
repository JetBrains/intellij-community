// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.ProjectFrameHelper

class SuperUserSuffixTitleInfoProvider : SimpleTitleInfoProvider(TitleInfoOption.ALWAYS_ACTIVE) {
  override val value: String = prepareValue()

  private fun prepareValue(): String {
    val vl = ProjectFrameHelper.getSuperUserSuffix()
    return vl?.let {
      if(IdeFrameDecorator.isCustomDecorationActive()) vl else "($vl)"
    } ?: ""
  }

  override val borderlessPrefix: String = " - "
}