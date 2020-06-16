// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.util.ui.codereview.BaseHtmlEditorPane
import icons.GithubIcons

internal class HtmlEditorPane() : BaseHtmlEditorPane(GithubIcons::class.java) {
  constructor(body: String) : this() {
    setBody(body)
  }
}