package com.intellij.htmltools

import com.intellij.util.PlatformUtils

object HtmlToolsTestsUtil {

  @JvmStatic
  fun isCommunityContext(): Boolean {
    return PlatformUtils.isCommunityEdition()
  }

}