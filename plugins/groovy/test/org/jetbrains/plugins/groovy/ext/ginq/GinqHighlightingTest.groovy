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
}
