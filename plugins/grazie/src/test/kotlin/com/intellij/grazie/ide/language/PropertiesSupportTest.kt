// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.spellchecker.grazie.GrazieSpellCheckerEngine
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.ui.UIUtil
import java.nio.charset.StandardCharsets


class PropertiesSupportTest : GrazieTestBase() {
  fun `test grammar check in file`() {
    EncodingProjectManager.getInstance(project).setDefaultCharsetForPropertiesFiles(null, StandardCharsets.UTF_8)
    EncodingProjectManager.getInstance(project).setNative2AsciiForPropertiesFiles(null, false)
    
    UIUtil.dispatchAllInvocationEvents()
    runHighlightTestForFile("ide/language/properties/Example.properties")
  }

  fun `test properties typos spellcheck performance`() {
    Benchmark.newBenchmark("Highlight typos in i18n.properties file") {
      runHighlightTestForFile("ide/language/properties/i18n.properties")
    }.setup {
      psiManager.dropPsiCaches()
      project.service<GrazieSpellCheckerEngine>().dropSuggestionCache()
    }.start()
  }
}
