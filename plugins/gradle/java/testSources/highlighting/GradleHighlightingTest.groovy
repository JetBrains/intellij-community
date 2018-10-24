// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.highlighting

import com.intellij.psi.PsiMethod
import org.junit.Test

class GradleHighlightingTest extends GradleHighlightingBaseTest {
  @Test
  void testConfiguration() throws Exception {
    testHighlighting """
configurations.create("myConfiguration")
configurations.myConfiguration {
    transitive = false
}
"""
  }

  @Test
  void testConfigurationResolve() throws Exception {
    def result = testResolve """
configurations.create("myConfiguration")
configurations.myConfiguration {
    transitive = false
}
""", "transitive"
    assert result instanceof PsiMethod
    assert result.getName() == "setTransitive"
  }
}
