// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

@CompileStatic
class GinqHighlightingTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GinqTestUtils.projectDescriptor

  @Override
  void setUp() throws Exception {
    super.setUp()
    GinqTestUtils.setUp(fixture)
    myFixture.enableInspections(GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection)
  }

  void testKeywordHighlighting() {
    testHighlighting """
<info descr="null">GQ</info> {
    <info descr="null"><info descr="null">from</info></info> n in [1, 2, 3]
    <info descr="null"><info descr="null">select</info></info> n
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

  void testExists() {
    testHighlighting """
GQ {
    from n in [1, 2, 3]
    where (from m in [2, 3] where m == n select m).exists()
    select n
}"""
  }

  void testJoin() {
    testHighlighting """
GQ {
    from n1 in [1, 2, 3]
    join n2 in [1, 3] on n1 == n2
    select n1, n2
}
"""
  }

  void testInnerJoin() {
    testHighlighting """
GQ {
  from n1 in [1, 2, 3]
  innerjoin n2 in [1, 3] on n1 == n2
  select n1, n2
}"""
    testHighlighting """
GQ {
  from n1 in [1, 2, 3]
  innerhashjoin n2 in [1, 3] on n1 == n2
  select n1, n2
}"""
  }

  void testLeftJoin() {
    testHighlighting """
GQ {
  from n1 in [1, 2, 3]
  leftjoin n2 in [2, 3, 4] on n1 == n2
  select n1, n2
}
"""
    testHighlighting """
GQ {
  from n1 in [1, 2, 3]
  lefthashjoin n2 in [2, 3, 4] on n1 == n2
  select n1, n2
}"""
  }

  void testRightJoin() {
    testHighlighting """
GQ {
  from n1 in [1, 2, 3]
  rightjoin n2 in [2, 3, 4] on n1 == n2
  select n1, n2
}
"""
    testHighlighting """
GQ {
  from n1 in [1, 2, 3]
  righthashjoin n2 in [2, 3, 4] on n1 == n2
  select n1, n2
}"""
  }

  void testFullJoin() {
    testHighlighting """
GQ {
  from n1 in [1, 2, 3]
  fulljoin n2 in [2, 3, 4] on n1 == n2
  select n1, n2
}
"""
    testHighlighting """
GQ {
  from n1 in [1, 2, 3]
  fullhashjoin n2 in [2, 3, 4] on n1 == n2
  select n1, n2
}"""
  }

  void testCrossJoin() {
    testHighlighting """
GQ {
  from n1 in [1, 2, 3]
  crossjoin n2 in [3, 4, 5]
  select n1, n2
}
"""
  }
}
