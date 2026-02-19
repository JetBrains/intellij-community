// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;

@SuppressWarnings("SpellCheckingInspection")
public class GinqHighlightingTest extends GrHighlightingTestBase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
  }

  private void testGinqHighlighting(String ginqContents) {
    doTestHighlighting("GQ {\n " + ginqContents + " \n}");
  }

  private void testGinqMethodHighlighting(String ginqContents) {
    doTestHighlighting("""
                         
                         import groovy.ginq.transform.GQ
                         
                         @GQ
                         def foo() {
                          \s""" + ginqContents + """
                         \s
                         }""");
  }

  public void testKeywordHighlighting() {
    doTestHighlighting("""
                         
                         <info descr="null">GQ</info> {
                             <info descr="null">from</info> n in [1, 2, 3]
                             <info descr="null">select</info> n
                         }
                         """, false, true, false);
  }

  public void testGinqFromStream() {
    testGinqHighlighting("from n in [1, 2, 3].stream() select n");
  }

  public void testGinqFromArray() {
    testGinqHighlighting("from n in new int[] {1, 2, 3} select n");
  }

  public void testGinqFromOtherGinq() {
    testGinqHighlighting("from n in (from m in [1, 2, 3] select m) select n");
  }

  public void testDistinct() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 2, 3, 3, 3]
                               select distinct(n)
                           """);
  }

  public void testDistinct2() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 2, 3, 3, 3]
                               select distinct(n, n + 1)
                           """);
  }

  public void testUnresolvedBinding() {
    testGinqHighlighting("""
                           
                               from n in [1]
                               select <warning>m</warning>
                           """);
  }

  public void testProjections() {
    testGinqHighlighting("""
                           
                               from v in (
                                   from n in [1, 2, 3]
                                   select n, Math.pow(n, 2) as powerOfN
                               )
                               select v.n, v.powerOfN
                           """);
  }

  public void testTwoGinqExpressions() {
    doTestHighlighting("""
                         
                         <info descr="null">GQ</info> {
                           <info descr="null">from</info> n in [0]
                           <info descr="null">where</info> n in (<info descr="null">from</info> m in [1] <info descr="null">select</info> m) && n in (<info descr="null">from</info> m in [1] <info descr="null">select</info> m)
                           <info descr="null">select</info> n
                         }
                         """, false, true, false);
  }

  public void testExists() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 3]
                               where (from m in [2, 3] where m == n select m).exists()
                               select n
                           """);
  }

  public void testJoin() {
    testGinqHighlighting("""
                           
                               from n1 in [1, 2, 3]
                               join n2 in [1, 3] on n1 == n2
                               select n1, n2
                           """);
  }

  public void testInnerJoin() {
    testGinqHighlighting("""
                           
                             from n1 in [1, 2, 3]
                             innerjoin n2 in [1, 3] on n1 == n2
                             select n1, n2
                           """);
    testGinqHighlighting("""
                           
                             from n1 in [1, 2, 3]
                             innerhashjoin n2 in [1, 3] on n1 == n2
                             select n1, n2
                           """);
  }

  public void testLeftJoin() {
    testGinqHighlighting("""
                           
                             from n1 in [1, 2, 3]
                             leftjoin n2 in [2, 3, 4] on n1 == n2
                             select n1, n2
                           """);
    testGinqHighlighting("""
                           
                             from n1 in [1, 2, 3]
                             lefthashjoin n2 in [2, 3, 4] on n1 == n2
                             select n1, n2
                           """);
  }

  public void testRightJoin() {
    testGinqHighlighting("""
                           
                             from n1 in [1, 2, 3]
                             rightjoin n2 in [2, 3, 4] on n1 == n2
                             select n1, n2
                           """);
    testGinqHighlighting("""
                           
                             from n1 in [1, 2, 3]
                             righthashjoin n2 in [2, 3, 4] on n1 == n2
                             select n1, n2
                           """);
  }

  public void testFullJoin() {
    testGinqHighlighting("""
                           
                             from n1 in [1, 2, 3]
                             fulljoin n2 in [2, 3, 4] on n1 == n2
                             select n1, n2
                           """);
    testGinqHighlighting("""
                           
                             from n1 in [1, 2, 3]
                             fullhashjoin n2 in [2, 3, 4] on n1 == n2
                             select n1, n2
                           """);
  }

  public void testCrossJoin() {
    testGinqHighlighting("""
                           
                             from n1 in [1, 2, 3]
                             crossjoin n2 in [3, 4, 5]
                             select n1, n2
                           """);
  }

  public void testGroupby() {
    testGinqHighlighting("""
                           
                           from n in [1, 1, 3, 3, 6, 6, 6]
                           groupby n
                           select n, count(n)
                           """);
  }

  public void testGroupbyHaving() {
    testGinqHighlighting("""
                           
                           from n in [1, 1, 3, 3, 6, 6, 6]
                           groupby n
                           having n >= 3
                           select n, count()
                           """);
  }

  public void testGroupbyAs() {
    testGinqHighlighting("""
                           
                           from s in ['ab', 'ac', 'bd', 'acd', 'bcd', 'bef']
                           groupby s.size() as length, s[0] as firstChar
                           having length == 3 && firstChar == 'b'
                           select length, firstChar, max(s)
                           """);
  }

  public void testCount() {
    testGinqHighlighting("""
                           
                           from n in [1, 1, 3, 3, 6, 6, 6]
                           groupby n
                           select n, count()
                           """);
  }

  public void testMin() {
    testGinqHighlighting("""
                           
                           from s in ['a', 'b', 'cd', 'ef']
                           groupby s.size() as length
                           select length, min(s)
                           """);
  }

  public void testMax() {
    testGinqHighlighting("""
                           
                           from s in ['a', 'b', 'cd', 'ef']
                           groupby s.size() as length
                           select length, max(s)
                           """);
  }

  public void testSum() {
    testGinqHighlighting("""
                           
                           from n in [1, 1, 3, 3, 6, 6, 6]
                           groupby n
                           select n, sum(n)
                           """);
  }

  public void testAvg() {
    testGinqHighlighting("""
                           
                           from n in [1, 1, 3, 3, 6, 6, 6]
                           groupby n
                           select n, avg(n)
                           """);
  }

  public void testMedian() {
    testGinqHighlighting("""
                           
                           from n in [1, 1, 3, 3, 6, 6, 6]
                           groupby n
                           select n, median(n)
                           """);
  }

  public void testAgg() {
    testGinqHighlighting("""
                           
                           from n in [1, 1, 3, 3, 6, 6, 6]
                           groupby n
                           select n, agg(_g.stream().map(r -> r.n.toBigDecimal()).reduce(BigDecimal.ZERO, BigDecimal::add))
                           """);
  }

  public void testStdev() {
    testGinqHighlighting("""
                           
                           from n in [1, 2, 3]
                           select stdev(n)
                           """);
  }

  public void testStdevp() {
    testGinqHighlighting("""
                           
                           from n in [1, 2, 3]
                           select stdevp(n)
                           """);
  }

  public void testVar() {
    testGinqHighlighting("""
                           
                           from n in [1, 2, 3]
                           select var(n)
                           """);
  }

  public void testVarp() {
    testGinqHighlighting("""
                           
                           from n in [1, 2, 3]
                           select varp(n)
                           """);
  }

  public void testOrderby() {
    testGinqHighlighting("""
                           
                           from n in [1, 5, 2, 6]
                           orderby n
                           select n
                           """);
  }

  public void testOrderbyInAsc() {
    testGinqHighlighting("""
                           
                           from n in [1, 5, 2, 6]
                           orderby n in asc
                           select n
                           """);
  }

  public void testOrderbyInDesc() {
    testGinqHighlighting("""
                           
                           from n in [1, 5, 2, 6]
                           orderby n in desc
                           select n
                           """);
  }

  public void testOrderbyDouble() {
    testGinqHighlighting("""
                           
                           from s in ['a', 'b', 'ef', 'cd']
                           orderby s.length() in desc, s in asc
                           select s
                           """);
  }

  public void testOrderbyMixed() {
    testGinqHighlighting("""
                           
                           from s in ['a', 'b', 'ef', 'cd']
                           orderby s.length() in desc, s
                           select s
                           """);
  }

  public void testOrderbyAscNullsLast() {
    testGinqHighlighting("""
                           
                           from n in [1, null, 5, null, 2, 6]
                           orderby n in asc(nullslast)
                           select n
                           """);
  }

  public void testOrderbyAscNullsFirst() {
    testGinqHighlighting("""
                           
                           from n in [1, null, 5, null, 2, 6]
                           orderby n in asc(nullsfirst)
                           select n
                           """);
  }

  public void testOrderbyDescNullsLast() {
    testGinqHighlighting("""
                           
                           from n in [1, null, 5, null, 2, 6]
                           orderby n in desc(nullslast)
                           select n
                           """);
  }

  public void testOrderbyDescNullsFirst() {
    testGinqHighlighting("""
                           
                           from n in [1, null, 5, null, 2, 6]
                           orderby n in desc(nullsfirst)
                           select n
                           """);
  }

  public void testLimit() {
    testGinqHighlighting("""
                           
                           from n in [1, 2, 3, 4, 5]
                           limit 3
                           select n
                           """);
  }

  public void testLimitAndOffset() {
    testGinqHighlighting("""
                           
                           from n in [1, 2, 3, 4, 5]
                           limit 1, 3
                           select n
                           """);
  }

  public void testAliasInParentheses() {
    testGinqHighlighting("""
                           
                           from n in [1]
                           select (n as e)
                           """);
  }

  public void testRowNumber1() {
    testGinqHighlighting("""
                           
                           from n in [2, 1, null, 3]
                               select n, (rowNumber() over(orderby n)),
                                         (rowNumber() over(orderby n in asc)),
                                         (rowNumber() over(orderby n in desc))
                           """);
  }

  public void testRowNumber2() {
    testGinqHighlighting("""
                           
                           from n in [1, 2, null, 3]
                               select n, (rowNumber() over(orderby n in asc(nullslast))),
                                         (rowNumber() over(orderby n in asc(nullsfirst))),
                                         (rowNumber() over(orderby n in desc(nullslast))),
                                         (rowNumber() over(orderby n in desc(nullsfirst)))
                           """);
  }

  public void testRank() {
    testGinqHighlighting("""
                           
                           from s in ['a', 'b', 'b', 'c', 'c', 'd', 'e']
                               select s,\s
                                   (rank() over(orderby s)),
                                   (denseRank() over(orderby s))
                           """);
  }

  public void testRank2() {
    testGinqHighlighting("""
                           
                           from n in [60, 60, 80, 80, 100]
                               select n,
                                   (percentRank() over(orderby n)),
                                   (cumeDist() over(orderby n))
                           """);
  }

  public void testRank3() {
    testGinqHighlighting("""
                           
                           from n in 1..10
                               select n, (ntile(4) over(orderby n))
                           """);
  }

  public void testLead1() {
    testGinqHighlighting("""
                           
                           from n in [2, 1, 3]
                               select n, (lead(n) over(orderby n))
                           """);
  }

  public void testLead2() {
    testGinqHighlighting("""
                           
                           from n in [2, 1, 3]
                               select n, (lead(n) over(orderby n in asc))
                           """);
  }

  public void testLead3() {
    testGinqHighlighting("""
                           
                           from s in ['a', 'ab', 'b', 'bc']
                               select s, (lead(s) over(orderby s.length(), s in desc))
                           """);
  }

  public void testLead4() {
    testGinqHighlighting("""
                           
                           from s in ['a', 'ab', 'b', 'bc']
                               select s, (lead(s) over(partitionby s.length() orderby s.length(), s in desc))
                           """);
  }

  public void testLead5() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (lag(n) over(orderby n))
                           """);
  }

  public void testLead6() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (lag(n) over(orderby n in desc))
                           """);
  }

  public void testLead7() {
    testGinqHighlighting("""
                           
                               from s in ['a', 'b', 'aa', 'bb']
                               select s, (lag(s) over(partitionby s.length() orderby s))
                           """);
  }

  public void testLead8() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (lead(n) over(orderby n)), (lag(n) over(orderby n))
                           """);
  }

  public void testLead9() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (lead(n, 2) over(orderby n)), (lag(n, 2) over(orderby n))
                           """);
  }

  public void testLead10() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (lead(n, 2, 'NONE') over(orderby n)), (lag(n, 2, 'NONE') over(orderby n))
                           """);
  }

  public void testFirstValue1() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (firstValue(n) over(orderby n rows -1, 1))
                           """);
  }

  public void testLastValue1() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (lastValue(n) over(orderby n rows -1, 1))
                           """);
  }

  public void testFirstValue2() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (firstValue(n) over(orderby n rows 0, 1))
                           """);
  }

  public void testFirstValue3() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (firstValue(n) over(orderby n rows -2, -1))
                           """);
  }

  public void testLastValue2() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (lastValue(n) over(orderby n rows -2, -1))
                           """);
  }

  public void testLastValue3() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (lastValue(n) over(orderby n rows 1, 2))
                           """);
  }

  public void testFirstValue4() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (firstValue(n) over(orderby n rows 1, 2))
                           """);
  }

  public void testLastValue4() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (lastValue(n) over(orderby n rows -1, 0))
                           """);
  }

  public void testFirstValue5() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (firstValue(n) over(orderby n rows null, 1))
                           """);
  }

  public void testLastValue5() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3]
                               select n, (lastValue(n) over(orderby n rows -1, null))
                           """);
  }

  public void testValues1() {
    testGinqHighlighting("""
                           
                               from s in ['a', 'aa', 'b', 'bb']
                               select s, (firstValue(s) over(partitionby s.length() orderby s)),
                                       (lastValue(s) over(partitionby s.length() orderby s))
                           """);
  }

  public void testValues2() {
    testGinqHighlighting("""
                           
                               from n in 1..3
                               select n, (nthValue(n, 0) over(orderby n)),
                                         (nthValue(n, 1) over(orderby n)),
                                         (nthValue(n, 2) over(orderby n)),
                                         (nthValue(n, 3) over(orderby n))
                           """);
  }

  public void testMinMax() {
    testGinqHighlighting("""
                           
                               from s in ['a', 'b', 'aa', 'bb']
                               select s, (min(s) over(partitionby s.length())), (max(s) over(partitionby s.length()))
                           """);
  }

  public void testPartitionby() {
    testGinqHighlighting("""
                           
                               from n in [1, 1, 2, 2, 3, 3]
                               select n, (count() over(partitionby n)),
                                         (count(n) over(partitionby n)),
                                         (sum(n) over(partitionby n)),
                                         (avg(n) over(partitionby n)),
                                         (median(n) over(partitionby n))
                           """);
  }

  public void testEmptyOver() {
    testGinqHighlighting("""
                           
                               from n in [2, 1, 3, null]
                               select n, (sum(n) over()),\s
                                         (max(n) over()),\s
                                         (min(n) over()),
                                         (count(n) over()),
                                         (count() over())
                           """);
  }

  public void testNegativeRange() {
    testGinqHighlighting("""
                           
                           from n in [1, 2, 5, 5]
                               select n, (count() over(orderby n range -2, 0)),
                                         (sum(n) over(orderby n range -2, 0))
                           """);
  }

  public void testRange() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 5, 5]
                               select n, (count() over(orderby n range 0, 1)),
                                         (sum(n) over(orderby n range 0, 1))
                           """);
  }

  public void testRange2() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 5, 5]
                               select n, (count() over(orderby n range -1, 1)),\s
                                         (sum(n) over(orderby n range -1, 1))
                           """);
  }

  public void testDescRange() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 5, 5]
                               select n, (count() over(orderby n in desc range 1, 2)),\s
                                         (sum(n) over(orderby n in desc range 1, 2))
                           """);
  }

  public void testDescRange2() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 5, 5]
                               select n, (count() over(orderby n in desc range -2, -1)),\s
                                         (sum(n) over(orderby n in desc range -2, -1))
                           """);
  }

  public void testNullRange() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 5, 5]
                               select n, (count() over(orderby n range 1, null)),\s
                                         (sum(n) over(orderby n range 1, null))
                           """);
  }

  public void testNullRange2() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 5, 5]
                               select n, (count() over(orderby n range null, 1)),\s
                                         (sum(n) over(orderby n range null, 1))
                           """);
  }

  public void testStdev2() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 3]
                               select n, (stdev(n) over())
                           """);
  }

  public void testStdevp2() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 3]
                               select n, (stdevp(n) over())
                           """);
  }

  public void testVar2() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 3]
                               select n, (var(n) over())
                           """);
  }

  public void testVarp2() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 3]
                               select n, (varp(n) over())
                           """);
  }

  public void testAgg2() {
    testGinqHighlighting("""
                           
                               from n in [1, 2, 3]
                               select n,
                                      (agg(_g.stream().map(r -> r.n.toBigDecimal()).reduce(BigDecimal.ZERO, BigDecimal::add)) over(partitionby n % 2))
                           """);
  }

  public void test_rn() {
    testGinqHighlighting("""
                           
                           from n in [1, 2, 3]\s
                           select _rn, n
                           """);
  }

  public void testSwitch() {
    testGinqHighlighting("""
                           
                           from n in [1, 2, 3, 4]
                           select switch (n) {
                               case 1 -> 'a'
                               case 2 -> 'b'
                               default -> 'c'
                           }
                           """);
  }

  public void testIncorrectFrom() {
    testGinqHighlighting("""
                           
                           from <error>a</error>
                           select b
                           """);
  }

  public void testIncorrectAlias() {
    testGinqHighlighting("""
                           
                           from <error>10</error> in 10
                           select b
                           """);
  }

  public void testIncompleteDataSource() {
    testGinqHighlighting("""
                           
                           (from a <error>in</error><error>)</error>
                           """);
  }

  public void testIncorrectJoin() {
    testGinqHighlighting("""
                           
                           from a in [1]
                           join <error>e</error>
                           select a
                           """);
  }

  public void testIncorrectJoinAlias() {
    testGinqHighlighting("""
                           
                           from a in [1]
                           join <error>10</error> in [1]
                           select a
                           """);
  }

  public void testIncompleteJoinDataSource() {
    testGinqHighlighting("""
                           
                           from a in [1]
                           (join e <error>in</error><error>)</error>
                           select a
                           """);
  }

  public void testIncorrectWhereBlock() {
    testGinqHighlighting("""
                           
                           from a in [1]
                           where <error>1, 2</error>
                           select a
                           """);
  }

  public void testMisplacedOn() {
    testGinqHighlighting("""
                           
                           from a in [1]
                           <error>on 1</error>
                           select a
                           """);
  }

  public void testEmptyLimit() {
    testGinqHighlighting("""
                           
                           from a in [1]
                           limit<error>()</error>
                           select a
                           """);
  }

  public void testTooBigLimit() {
    testGinqHighlighting("""
                           
                           from a in [1]
                           limit <error>1, 2, 3</error>
                           select a
                           """);
  }

  public void testUnrecognizedQuery() {
    testGinqHighlighting("""
                           
                           <error>foo</error> 1
                           """);
  }

  public void testMisplacedQueries() {
    testGinqHighlighting("""
                           
                           from a in [1]
                           <error>where</error> true
                           <error>join</error> e in [2] on e == e
                           select a
                           """);
  }

  public void testFromShouldBeFirst() {
    testGinqHighlighting("""
                           
                           <error>join</error> a in [1]
                           where true
                           """);
  }

  public void testSelectShouldBeLast() {
    testGinqHighlighting("""
                           
                           <error>from</error> b in [1]
                           join a in [1] on a == b
                           """);
  }

  public void testOnWithKw() {
    testGinqHighlighting("""
                           
                           from x in [1]
                           join x1 in [2] on true
                           select x
                           """);
  }

  public void testWhereTyped() {
    testGinqHighlighting("""
                           
                                   from aa in [1]
                                   where <warning>aa</warning>
                                   select aa as e1
                           """);
  }

  public void testDataSourceTyped() {
    testGinqHighlighting("""
                           
                                   from aa in <warning>1</warning>
                                   select aa as e1
                           """);
  }

  public void testDataSourceTyped2() {
    testGinqHighlighting("""
                           
                                   from aa in []
                                   select aa as e1
                           """);
  }

  public void testJoinWithoutOn() {
    testGinqHighlighting("""
                           
                                   from aa in [1]
                                   <error>join</error> x in [2]
                                   select aa as e1
                           """);
  }

  public void testCrossjoinWithOn() {
    testGinqHighlighting("""
                           
                                   from aa in [1]
                                   <error>crossjoin x in [2] on x == aa</error>
                                   select aa as e1
                           """);
  }

  public void testMethodJoin() {
    testGinqMethodHighlighting("""
                                 
                                 from n1 in [1, 2, 3]
                                 fulljoin n2 in [2, 3, 4] on n1 == n2
                                 select n1, n2
                                 """);
  }

  public void testMethodGroupby() {
    testGinqMethodHighlighting("""
                                 
                                 from n in [1, 1, 3, 3, 6, 6, 6]
                                 groupby n
                                 select n, count(n)
                                 """);
  }

  public void testMethodOrderby() {
    testGinqMethodHighlighting("""
                                 
                                 from s in ['a', 'b', 'ef', 'cd']
                                 orderby s.length() in desc, s
                                 select s
                                 """);
  }

  public void testMethodWindows() {
    testGinqMethodHighlighting("""
                                 
                                 from s in ['a', 'b', 'aa', 'bb']
                                 select s, (min(s) over(partitionby s.length())), (max(s) over(partitionby s.length()))
                                 """);
  }

  public void testMethod_rn() {
    testGinqMethodHighlighting("""
                                 
                                 from n in [1, 2, 3]\s
                                 select _rn, n
                                 """);
  }

  public void testMethodIncompleteJoinDataSource() {
    testGinqMethodHighlighting("""
                                 
                                 from a in [1]
                                 (join e <error>in</error><error>)</error>
                                 select a
                                 """);
  }

  public void testMethodWhereTyped() {
    testGinqMethodHighlighting("""
                                 
                                 from aa in [1]
                                 where <warning>aa</warning>
                                 select aa as e1
                                 """);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GinqTestUtils.getProjectDescriptor();
  }
}
