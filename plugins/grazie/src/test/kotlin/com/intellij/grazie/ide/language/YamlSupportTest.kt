// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase

class YamlSupportTest : GrazieTestBase() {
  fun `test grammar check in yaml file`() {
    runHighlightTestForFile("ide/language/yaml/Example.yaml")
  }
}
