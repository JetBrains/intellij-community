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
    myFixture.enableInspections(GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection)
  }

  private void testGinqHighlighting(String ginqContents) {
    doTestHighlighting("GQ {\n $ginqContents \n}")
  }

  private void testGinqMethodHighlighting(String ginqContents) {
    doTestHighlighting("""
import groovy.ginq.transform.GQ

@GQ
def foo() {
  $ginqContents 
}""")
  }

  void testKeywordHighlighting() {
    doTestHighlighting """
<info descr="null">GQ</info> {
    <info descr="null">from</info> n in [1, 2, 3]
    <info descr="null">select</info> n
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
    doTestHighlighting """
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

  void testRowNumber1() {
    testGinqHighlighting """
from n in [2, 1, null, 3]
    select n, (rowNumber() over(orderby n)),
              (rowNumber() over(orderby n in asc)),
              (rowNumber() over(orderby n in desc))
"""
  }

  void testRowNumber2() {
    testGinqHighlighting """
from n in [1, 2, null, 3]
    select n, (rowNumber() over(orderby n in asc(nullslast))),
              (rowNumber() over(orderby n in asc(nullsfirst))),
              (rowNumber() over(orderby n in desc(nullslast))),
              (rowNumber() over(orderby n in desc(nullsfirst)))
"""
  }

  void testRank() {
    testGinqHighlighting """
from s in ['a', 'b', 'b', 'c', 'c', 'd', 'e']
    select s, 
        (rank() over(orderby s)),
        (denseRank() over(orderby s))
"""
  }

  void testRank2() {
    testGinqHighlighting """
from n in [60, 60, 80, 80, 100]
    select n,
        (percentRank() over(orderby n)),
        (cumeDist() over(orderby n))
"""
  }

  void testRank3() {
    testGinqHighlighting """
from n in 1..10
    select n, (ntile(4) over(orderby n))
"""
  }

  void testLead1() {
    testGinqHighlighting """
from n in [2, 1, 3]
    select n, (lead(n) over(orderby n))
"""
  }

  void testLead2() {
    testGinqHighlighting """
from n in [2, 1, 3]
    select n, (lead(n) over(orderby n in asc))
"""
  }

  void testLead3() {
    testGinqHighlighting """
from s in ['a', 'ab', 'b', 'bc']
    select s, (lead(s) over(orderby s.length(), s in desc))
"""
  }

  void testLead4() {
    testGinqHighlighting """
from s in ['a', 'ab', 'b', 'bc']
    select s, (lead(s) over(partitionby s.length() orderby s.length(), s in desc))
"""
  }

  void testLead5() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (lag(n) over(orderby n))
"""
  }

  void testLead6() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (lag(n) over(orderby n in desc))
"""
  }

  void testLead7() {
    testGinqHighlighting """
    from s in ['a', 'b', 'aa', 'bb']
    select s, (lag(s) over(partitionby s.length() orderby s))
"""
  }

  void testLead8() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (lead(n) over(orderby n)), (lag(n) over(orderby n))
"""
  }

  void testLead9() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (lead(n, 2) over(orderby n)), (lag(n, 2) over(orderby n))
"""
  }

  void testLead10() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (lead(n, 2, 'NONE') over(orderby n)), (lag(n, 2, 'NONE') over(orderby n))
"""
  }

  void testFirstValue1() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (firstValue(n) over(orderby n rows -1, 1))
"""
  }

  void testLastValue1() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (lastValue(n) over(orderby n rows -1, 1))
"""
  }

  void testFirstValue2() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (firstValue(n) over(orderby n rows 0, 1))
"""
  }

  void testFirstValue3() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (firstValue(n) over(orderby n rows -2, -1))
"""
  }

  void testLastValue2() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (lastValue(n) over(orderby n rows -2, -1))
"""
  }

  void testLastValue3() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (lastValue(n) over(orderby n rows 1, 2))
"""
  }

  void testFirstValue4() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (firstValue(n) over(orderby n rows 1, 2))
"""
  }

  void testLastValue4() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (lastValue(n) over(orderby n rows -1, 0))
"""
  }

  void testFirstValue5() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (firstValue(n) over(orderby n rows null, 1))
"""
  }

  void testLastValue5() {
    testGinqHighlighting """
    from n in [2, 1, 3]
    select n, (lastValue(n) over(orderby n rows -1, null))
"""
  }

  void testValues1() {
    testGinqHighlighting """
    from s in ['a', 'aa', 'b', 'bb']
    select s, (firstValue(s) over(partitionby s.length() orderby s)),
            (lastValue(s) over(partitionby s.length() orderby s))
"""
  }

  void testValues2() {
    testGinqHighlighting """
    from n in 1..3
    select n, (nthValue(n, 0) over(orderby n)),
              (nthValue(n, 1) over(orderby n)),
              (nthValue(n, 2) over(orderby n)),
              (nthValue(n, 3) over(orderby n))
"""
  }

  void testMinMax() {
    testGinqHighlighting """
    from s in ['a', 'b', 'aa', 'bb']
    select s, (min(s) over(partitionby s.length())), (max(s) over(partitionby s.length()))
"""
  }

  void testPartitionby() {
    testGinqHighlighting """
    from n in [1, 1, 2, 2, 3, 3]
    select n, (count() over(partitionby n)),
              (count(n) over(partitionby n)),
              (sum(n) over(partitionby n)),
              (avg(n) over(partitionby n)),
              (median(n) over(partitionby n))
"""
  }

  void testEmptyOver() {
    testGinqHighlighting """
    from n in [2, 1, 3, null]
    select n, (sum(n) over()), 
              (max(n) over()), 
              (min(n) over()),
              (count(n) over()),
              (count() over())
"""
  }

  void testNegativeRange() {
    testGinqHighlighting """
from n in [1, 2, 5, 5]
    select n, (count() over(orderby n range -2, 0)),
              (sum(n) over(orderby n range -2, 0))
"""
  }

  void testRange() {
    testGinqHighlighting """
    from n in [1, 2, 5, 5]
    select n, (count() over(orderby n range 0, 1)),
              (sum(n) over(orderby n range 0, 1))
"""
  }

  void testRange2() {
    testGinqHighlighting """
    from n in [1, 2, 5, 5]
    select n, (count() over(orderby n range -1, 1)), 
              (sum(n) over(orderby n range -1, 1))
"""
  }

  void testDescRange() {
    testGinqHighlighting """
    from n in [1, 2, 5, 5]
    select n, (count() over(orderby n in desc range 1, 2)), 
              (sum(n) over(orderby n in desc range 1, 2))
"""
  }

  void testDescRange2() {
    testGinqHighlighting """
    from n in [1, 2, 5, 5]
    select n, (count() over(orderby n in desc range -2, -1)), 
              (sum(n) over(orderby n in desc range -2, -1))
"""
  }

  void testNullRange() {
    testGinqHighlighting """
    from n in [1, 2, 5, 5]
    select n, (count() over(orderby n range 1, null)), 
              (sum(n) over(orderby n range 1, null))
"""
  }

  void testNullRange2() {
    testGinqHighlighting """
    from n in [1, 2, 5, 5]
    select n, (count() over(orderby n range null, 1)), 
              (sum(n) over(orderby n range null, 1))
"""
  }

  void testStdev2() {
    testGinqHighlighting """
    from n in [1, 2, 3]
    select n, (stdev(n) over())
"""
  }

  void testStdevp2() {
    testGinqHighlighting """
    from n in [1, 2, 3]
    select n, (stdevp(n) over())
"""
  }

  void testVar2() {
    testGinqHighlighting """
    from n in [1, 2, 3]
    select n, (var(n) over())
"""
  }

  void testVarp2() {
    testGinqHighlighting """
    from n in [1, 2, 3]
    select n, (varp(n) over())
"""
  }

  void testAgg2() {
    testGinqHighlighting """
    from n in [1, 2, 3]
    select n,
           (agg(_g.stream().map(r -> r.n.toBigDecimal()).reduce(BigDecimal.ZERO, BigDecimal::add)) over(partitionby n % 2))
"""
  }

  void test_rn() {
    testGinqHighlighting """
from n in [1, 2, 3] 
select _rn, n
"""
  }

  void testSwitch() {
    testGinqHighlighting """
from n in [1, 2, 3, 4]
select switch (n) {
    case 1 -> 'a'
    case 2 -> 'b'
    default -> 'c'
}
"""
  }

  void testIncorrectFrom() {
    testGinqHighlighting """
from <error>a</error>
select b
"""
  }

  void testIncorrectAlias() {
    testGinqHighlighting """
from <error>10</error> in 10
select b
"""
  }

  void testIncompleteDataSource() {
    testGinqHighlighting """
(from a <error>in</error><error>)</error>
"""
  }

  void testIncorrectJoin() {
    testGinqHighlighting """
from a in [1]
join <error>e</error>
select a
"""
  }

  void testIncorrectJoinAlias() {
    testGinqHighlighting """
from a in [1]
join <error>10</error> in [1]
select a
"""
  }

  void testIncompleteJoinDataSource() {
    testGinqHighlighting """
from a in [1]
(join e <error>in</error><error>)</error>
select a
"""
  }

  void testIncorrectWhereBlock() {
    testGinqHighlighting """
from a in [1]
where <error>1, 2</error>
select a
"""
  }

  void testMisplacedOn() {
    testGinqHighlighting """
from a in [1]
<error>on 1</error>
select a
"""
  }

  void testEmptyLimit() {
    testGinqHighlighting """
from a in [1]
limit<error>()</error>
select a
"""
  }

  void testTooBigLimit() {
    testGinqHighlighting """
from a in [1]
limit <error>1, 2, 3</error>
select a
"""
  }

  void testUnrecognizedQuery() {
    testGinqHighlighting """
<error>foo</error> 1
"""
  }

  void testMisplacedQueries() {
    testGinqHighlighting """
from a in [1]
<error>where</error> true
<error>join</error> e in [2] on e == e
select a
"""
  }

  void testFromShouldBeFirst() {
    testGinqHighlighting """
<error>join</error> a in [1]
where true
"""
  }

  void testSelectShouldBeLast() {
    testGinqHighlighting """
<error>from</error> b in [1]
join a in [1] on a == b
"""
  }

  void testOnWithKw() {
    testGinqHighlighting """
from x in [1]
join x1 in [2] on true
select x
"""
  }

  void testWhereTyped() {
    testGinqHighlighting """
        from aa in [1]
        where <warning>aa</warning>
        select aa as e1
"""
  }

  void testDataSourceTyped() {
    testGinqHighlighting """
        from aa in <warning>1</warning>
        select aa as e1
"""
  }

  void testDataSourceTyped2() {
    testGinqHighlighting """
        from aa in []
        select aa as e1
"""
  }

  void testJoinWithoutOn() {
    testGinqHighlighting """
        from aa in [1]
        <error>join</error> x in [2]
        select aa as e1
"""
  }

  void testCrossjoinWithOn() {
    testGinqHighlighting """
        from aa in [1]
        <error>crossjoin x in [2] on x == aa</error>
        select aa as e1
"""
  }

  void testMethodJoin() {
    testGinqMethodHighlighting """
from n1 in [1, 2, 3]
fulljoin n2 in [2, 3, 4] on n1 == n2
select n1, n2
"""
  }

  void testMethodGroupby() {
    testGinqMethodHighlighting """
from n in [1, 1, 3, 3, 6, 6, 6]
groupby n
select n, count(n)
"""
  }

  void testMethodOrderby() {
    testGinqMethodHighlighting """
from s in ['a', 'b', 'ef', 'cd']
orderby s.length() in desc, s
select s
"""
  }

  void testMethodWindows() {
    testGinqMethodHighlighting """
from s in ['a', 'b', 'aa', 'bb']
select s, (min(s) over(partitionby s.length())), (max(s) over(partitionby s.length()))
"""
  }

  void testMethod_rn() {
    testGinqMethodHighlighting """
from n in [1, 2, 3] 
select _rn, n
"""
  }

  void testMethodIncompleteJoinDataSource() {
    testGinqMethodHighlighting """
from a in [1]
(join e <error>in</error><error>)</error>
select a
"""
  }

  void testMethodWhereTyped() {
    testGinqMethodHighlighting """
from aa in [1]
where <warning>aa</warning>
select aa as e1
"""
  }
}
