// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.markdown.utils.MarkdownToHtmlConverter
import com.intellij.openapi.util.NlsSafe
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor

object GitLabUIUtil {

  private val mdConverter = MarkdownToHtmlConverter(GFMFlavourDescriptor())

  internal fun convertToHtml(markdownSource: String): @NlsSafe String {
    return mdConverter.convertMarkdownToHtml(markdownSource)
  }
}