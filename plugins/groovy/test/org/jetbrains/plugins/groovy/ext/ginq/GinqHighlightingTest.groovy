// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

@CompileStatic
class GinqHighlightingTest extends BaseGinqTest {

  @Override
  void setUp() throws Exception {
    super.setUp()
    myFixture.enableInspections(GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection)
  }

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

  void testDistinct() {
    testHighlighting """GQ {
    from n in [1, 2, 2, 3, 3, 3]
    select distinct(n)
}"""
  }

  void testDistinct2() {
    testHighlighting """
GQ {
    from n in [1, 2, 2, 3, 3, 3]
    select distinct(n, n + 1)
}"""
  }

  void testUnresolvedBinding() {
    testHighlighting """GQ {
    from n in [1]
    select <warning>m</warning>
}"""
  }

  void testProjections() {
    testHighlighting """
GQ {
    from v in (
        from n in [1, 2, 3]
        select n, Math.pow(n, 2) as powerOfN
    )
    select v.n, v.powerOfN
}"""
  }

  void testTwoGinqExpressions() {
    testHighlighting """
<info descr="null">GQ</info> {
  <info descr="null">from</info> n in [0]
  <info descr="null">where</info> n in (<info descr="null">from</info> m in [1] <info descr="null">select</info> m) && n in (<info descr="null">from</info> m in [1] <info descr="null">select</info> m)
  <info descr="null">select</info> n
}
""", false, true, false
  }
}
