// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import org.jetbrains.plugins.gradle.highlighting.GradleHighlightingBaseTest
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToUtilKt
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

class GradleExtensionsTest extends GradleHighlightingBaseTest implements ResolveTest {

  @Test
  void "project level ext closure delegate type"() {
    doTest ("ext {<caret>}") {
      def ref = elementUnderCaret(GrClosableBlock)
      def info = GrDelegatesToUtilKt.getDelegatesToInfo(ref)
      assert  info != null
      assert info.typeToDelegate.equalsToText("org.gradle.api.internal.plugins.DefaultExtraPropertiesExtension")
    }
  }
}
