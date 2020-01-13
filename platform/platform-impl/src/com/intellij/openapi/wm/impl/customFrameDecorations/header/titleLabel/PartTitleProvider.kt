// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.openapi.util.ClassExtension
import com.intellij.openapi.wm.impl.TitleInfoProvider

class PartTitleProvider : ClassExtension<DefaultPartTitle>("com.intellij.borderlessPartTitleProvider") {
  companion object {
    val INSTANCE = PartTitleProvider()
  }

  fun getPartTitle(titleInfo: TitleInfoProvider): DefaultPartTitle {
    return INSTANCE.forClass(titleInfo.javaClass) ?: DefaultPartTitle(" ")
  }
}

class DashTitlePartTitle : DefaultPartTitle(" - ")

