// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import groovy.transform.CompileStatic

@CompileStatic
class GinqHighlightingTest extends BaseGinqTest {

  void testKeywordHighlighting() {
    testHighlighting """
<info descr="null">GQ</info> {
    <info descr="null">from</info> n in [1, 2, 3]
    <info descr="null">select</info> n
}
""", false, true, false
  }

  void testGinqFromStream() {
    testHighlighting """GQ { from n in [1, 2, 3].stream() select n }"""
  }

  void testGinqFromArray() {
    testHighlighting """GQ { from n in new int[] {1, 2, 3} select n }"""
  }

  void testGinqFromOtherGinq() {
    testHighlighting """GQ { from n in (from m in [1, 2, 3] select m) select n }"""
  }
}
