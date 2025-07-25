// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.util.ui.UIUtil
import java.nio.charset.StandardCharsets


class PropertiesSupportTest : GrazieTestBase() {
  fun `test grammar check in file`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))

    EncodingProjectManager.getInstance(project).setDefaultCharsetForPropertiesFiles(null, StandardCharsets.UTF_8)
    EncodingProjectManager.getInstance(project).setNative2AsciiForPropertiesFiles(null, false)
    
    UIUtil.dispatchAllInvocationEvents()
    runHighlightTestForFile("ide/language/properties/Example.properties")
  }
}
