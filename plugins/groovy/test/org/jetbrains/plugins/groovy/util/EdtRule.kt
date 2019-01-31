// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.testFramework.runInEdtAndWait
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class EdtRule : TestRule {

  override fun apply(base: Statement, description: Description?): Statement = object : Statement() {
    override fun evaluate() {
      runInEdtAndWait {
        base.evaluate()
      }
    }
  }
}
