// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.lang.properties.PropertiesLanguage


class PropertiesSupportTest : GrazieTestBase() {
  override val additionalEnabledContextLanguages = setOf(PropertiesLanguage.INSTANCE)

  fun `test grammar check in file`() {
    runHighlightTestForFile("ide/language/properties/Example.properties")
  }
}
