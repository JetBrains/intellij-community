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

  private void testGinqHighlighting(String ginqContents) {
    testHighlighting("GQ {\n $ginqContents \n}")
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
    testGinqHighlighting "from n in [1, 2, 3].stream() select n"
  }

  void testGinqFromArray() {
    testGinqHighlighting "from n in new int[] {1, 2, 3} select n"
  }

  void testGinqFromOtherGinq() {
    testGinqHighlighting "from n in (from m in [1, 2, 3] select m) select n"
  }

  void testDistinct() {
    testGinqHighlighting """
    from n in [1, 2, 2, 3, 3, 3]
    select distinct(n)
"""
  }

  void testDistinct2() {
    testGinqHighlighting """
    from n in [1, 2, 2, 3, 3, 3]
    select distinct(n, n + 1)
"""
  }

  void testUnresolvedBinding() {
    testGinqHighlighting """
    from n in [1]
    select <warning>m</warning>
"""
  }

  void testProjections() {
    testGinqHighlighting """
    from v in (
        from n in [1, 2, 3]
        select n, Math.pow(n, 2) as powerOfN
    )
    select v.n, v.powerOfN
"""
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
    testGinqHighlighting """
    from n in [1, 2, 3]
    where (from m in [2, 3] where m == n select m).exists()
    select n
"""
  }

  void testJoin() {
    testGinqHighlighting """
    from n1 in [1, 2, 3]
    join n2 in [1, 3] on n1 == n2
    select n1, n2
"""
  }

  void testInnerJoin() {
    testGinqHighlighting """
  from n1 in [1, 2, 3]
  innerjoin n2 in [1, 3] on n1 == n2
  select n1, n2
"""
    testGinqHighlighting """
  from n1 in [1, 2, 3]
  innerhashjoin n2 in [1, 3] on n1 == n2
  select n1, n2
"""
  }

  void testLeftJoin() {
    testGinqHighlighting """
  from n1 in [1, 2, 3]
  leftjoin n2 in [2, 3, 4] on n1 == n2
  select n1, n2
"""
    testGinqHighlighting """
  from n1 in [1, 2, 3]
  lefthashjoin n2 in [2, 3, 4] on n1 == n2
  select n1, n2
"""
  }

  void testRightJoin() {
    testGinqHighlighting """
  from n1 in [1, 2, 3]
  rightjoin n2 in [2, 3, 4] on n1 == n2
  select n1, n2
"""
    testGinqHighlighting """
  from n1 in [1, 2, 3]
  righthashjoin n2 in [2, 3, 4] on n1 == n2
  select n1, n2
"""
  }

  void testFullJoin() {
    testGinqHighlighting """
  from n1 in [1, 2, 3]
  fulljoin n2 in [2, 3, 4] on n1 == n2
  select n1, n2
"""
    testGinqHighlighting """
  from n1 in [1, 2, 3]
  fullhashjoin n2 in [2, 3, 4] on n1 == n2
  select n1, n2
"""
  }

  void testCrossJoin() {
    testGinqHighlighting """
  from n1 in [1, 2, 3]
  crossjoin n2 in [3, 4, 5]
  select n1, n2
"""
  }

  void testGroupby() {
    testGinqHighlighting """
from n in [1, 1, 3, 3, 6, 6, 6]
groupby n
select n, count(n)
"""
  }

  void testGroupbyHaving() {
    testGinqHighlighting """
from n in [1, 1, 3, 3, 6, 6, 6]
groupby n
having n >= 3
select n, count()
"""
  }

  void testGroupbyAs() {
    testGinqHighlighting """
from s in ['ab', 'ac', 'bd', 'acd', 'bcd', 'bef']
groupby s.size() as length, s[0] as firstChar
having length == 3 && firstChar == 'b'
select length, firstChar, max(s)
"""
  }

  void testCount() {
    testGinqHighlighting """
from n in [1, 1, 3, 3, 6, 6, 6]
groupby n
select n, count()
"""
  }

  void testMin() {
    testGinqHighlighting """
from s in ['a', 'b', 'cd', 'ef']
groupby s.size() as length
select length, min(s)
"""
  }

  void testMax() {
    testGinqHighlighting """
from s in ['a', 'b', 'cd', 'ef']
groupby s.size() as length
select length, max(s)
"""
  }

  void testSum() {
    testGinqHighlighting """
from n in [1, 1, 3, 3, 6, 6, 6]
groupby n
select n, sum(n)
"""
  }

  void testAvg() {
    testGinqHighlighting """
from n in [1, 1, 3, 3, 6, 6, 6]
groupby n
select n, avg(n)
"""
  }

  void testMedian() {
    testGinqHighlighting """
from n in [1, 1, 3, 3, 6, 6, 6]
groupby n
select n, median(n)
"""
  }

  void testAgg() {
    testGinqHighlighting """
from n in [1, 1, 3, 3, 6, 6, 6]
groupby n
select n, agg(_g.stream().map(r -> r.n.toBigDecimal()).reduce(BigDecimal.ZERO, BigDecimal::add))
"""
  }

  void testStdev() {
    testGinqHighlighting """
from n in [1, 2, 3]
select stdev(n)
"""
  }

  void testStdevp() {
    testGinqHighlighting """
from n in [1, 2, 3]
select stdevp(n)
"""
  }

  void testVar() {
    testGinqHighlighting """
from n in [1, 2, 3]
select var(n)
"""
  }

  void testVarp() {
    testGinqHighlighting """
from n in [1, 2, 3]
select varp(n)
"""
  }

  void testOrderby() {
    testGinqHighlighting """
from n in [1, 5, 2, 6]
orderby n
select n
"""
  }

  void testOrderbyInAsc() {
    testGinqHighlighting """
from n in [1, 5, 2, 6]
orderby n in asc
select n
"""
  }

  void testOrderbyInDesc() {
    testGinqHighlighting """
from n in [1, 5, 2, 6]
orderby n in desc
select n
"""
  }

  void testOrderbyDouble() {
    testGinqHighlighting """
from s in ['a', 'b', 'ef', 'cd']
orderby s.length() in desc, s in asc
select s
"""
  }

  void testOrderbyMixed() {
    testGinqHighlighting """
from s in ['a', 'b', 'ef', 'cd']
orderby s.length() in desc, s
select s
"""
  }

  void testOrderbyAscNullsLast() {
    testGinqHighlighting """
from n in [1, null, 5, null, 2, 6]
orderby n in asc(nullslast)
select n
"""
  }

  void testOrderbyAscNullsFirst() {
    testGinqHighlighting """
from n in [1, null, 5, null, 2, 6]
orderby n in asc(nullsfirst)
select n
"""
  }

  void testOrderbyDescNullsLast() {
    testGinqHighlighting """
from n in [1, null, 5, null, 2, 6]
orderby n in desc(nullslast)
select n
"""
  }

  void testOrderbyDescNullsFirst() {
    testGinqHighlighting """
from n in [1, null, 5, null, 2, 6]
orderby n in desc(nullsfirst)
select n
"""
  }

  void testLimit() {
    testGinqHighlighting """
from n in [1, 2, 3, 4, 5]
limit 3
select n
"""
  }

  void testLimitAndOffset() {
    testGinqHighlighting """
from n in [1, 2, 3, 4, 5]
limit 1, 3
select n
"""
  }

  void testAliasInParentheses() {
    testGinqHighlighting """
from n in [1]
select (n as e)
"""
  }

}
