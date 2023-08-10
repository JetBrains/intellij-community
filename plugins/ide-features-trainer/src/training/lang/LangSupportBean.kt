// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.lang

import com.intellij.lang.Language
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.TestOnly

class LangSupportBean : CustomLoadingExtensionPointBean<LangSupport> {
  /**
   * [Language.getID]
   */
  @Attribute("language")
  var language: String? = null

  /**
   * [com.intellij.openapi.application.ApplicationNamesInfo.getProductName]
   */
  @Attribute("defaultProductName")
  var defaultProductName: String? = null

  /**
   * [ToolWindowAnchor]
   */
  @Attribute("learnWindowAnchor")
  var learnWindowAnchor: String? = null

  @Attribute("implementationClass")
  var implementationClass: String? = null

  constructor() : super()

  @TestOnly
  constructor(language: String, instance: LangSupport) : super(instance) {
    this.language = language
  }


  fun getLang(): String = language ?: error("Language must be specified for bean: $implementationClass")

  fun getLearnToolWindowAnchor(): ToolWindowAnchor {
    return learnWindowAnchor?.let {
      ToolWindowAnchor.fromText(it)
    } ?: ToolWindowAnchor.LEFT
  }

  override fun getImplementationClassName() = implementationClass
}