// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.controlFlow


import org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceTestBase

/**
 * Contains samples of incorrect code, which although must not cause any problems to the CFG builder
 */
class GrControlFlowSanityTest extends TypeInferenceTestBase {



  void testCFG1() {
    doTest("""
A qualityToolConfigurator = new Aa()
qualityToolCo() {
  case "phpcs":
  qualityT<caret>oolConfigurator = 1
  break
}
""", "A")
  }
}
