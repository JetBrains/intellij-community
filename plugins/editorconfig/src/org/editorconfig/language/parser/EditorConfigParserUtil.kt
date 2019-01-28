// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase

object EditorConfigParserUtil : GeneratedParserUtilBase() {
  @Suppress("UNUSED_PARAMETER")
  @JvmStatic
  fun unbindComments(builder: PsiBuilder, level: Int): Boolean {
    val marker = builder.latestDoneMarker as? PsiBuilder.Marker
    marker?.setCustomEdgeTokenBinders(
      { tokens, _, _ -> tokens.size },
      { _, _, _ -> 0 }
    )
    return true
  }
}

