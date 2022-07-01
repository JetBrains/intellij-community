// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.completion.GroovyCompletionTestBase

class GinqCompletionTest extends GroovyCompletionTestBase {

  final LightProjectDescriptor projectDescriptor = GinqTestUtils.projectDescriptor

  @Override
  void setUp() throws Exception {
    super.setUp()
    myFixture.enableInspections(GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection)
  }

  private void completeGinq(String before, String after) {
    doBasicTest("GQ {\n$before\n}", "GQ {\n$after\n}")
  }

  private void completeMethodGinq(String before, String after) {
    def testCode = {
      """
import groovy.ginq.transform.GQ

@GQ
def foo() {
  $it 
}"""
    }
    doBasicTest(testCode(before), testCode(after))
  }

  private void noCompleteGinq(String before, String... excluded) {
    doNoVariantsTest("GQ { \n $before \n }", excluded)
  }

  void testFrom() {
    completeGinq('''\
fro<caret>
''', '''\
from x in
''')
  }

  void testSelect() {
    completeGinq('''\
from x in [1]
selec<caret>
''', '''\
from x in [1]
select
''')
  }

  void testBareJoin() {
    completeGinq('''\
from x in [1]
fullhashjoi<caret>
''', '''\
from x in [1]
fullhashjoin x1 in  on
''')
  }

  void testJoin() {
    completeGinq('''\
from x in [1] 
fullhashjoi<caret>
select x
''', '''\
from x in [1] 
fullhashjoin x1 in  on
select x
''')
  }

  void testWhere() {
    completeGinq('''\
from x in [1] 
whe<caret>
select x
''', '''\
from x in [1] 
where 
select x
''')
  }

  void testOn() {
    completeGinq('''\
from x in [1] 
join y in [1] o<caret>
select x
''', '''\
from x in [1] 
join y in [1] on
select x
''')
  }

  void testAfterCrossjoin() {
    completeGinq('''\
from x in [1] 
crossjoin y in [1]
whe<caret>
select x
''', '''\
from x in [1] 
crossjoin y in [1]
where 
select x
''')
  }

  void testNoOnAfterCrossjoin() {
    noCompleteGinq('''\
from x in [1] 
crossjoin y in [1] <caret>
select x
''', 'on')
  }

  void testNoJoinAfterJoin() {
    noCompleteGinq('''\
from x in [1] 
join y in [1] <caret>
select x
''', 'join', 'crossjoin')
  }


  void testCompleteCrossjoin() {
    completeGinq('''\
from x in [1] 
crossjo<caret>
select x
''', '''\
from x in [1] 
crossjoin x1 in 
select x
''')
  }

  void testCompleteBindings() {
    completeGinq('''
from xxxx in [1] 
where xxx<caret>
select xxxx
''', '''
from xxxx in [1] 
where xxxx
select xxxx
''')
  }

  void testCompleteInWindow() {
    completeGinq('''
from x in [1]
select rowNu<caret>
''', '''
from x in [1]
select (rowNumber() over ())
''')
  }

  void testCompleteInWindow2() {
    completeGinq('''
from x in [1]
select firstVal<caret>
''', '''
from x in [1]
select (firstValue() over ())
''')
  }

  void testCompleteInOver() {
    completeGinq('''
from x in [1]
select (rowNumber() over (orde<caret>))
''', '''
from x in [1]
select (rowNumber() over (orderby ))
''')
  }

  void testCompleteInOver2() {
    completeGinq('''
from x in [1]
select (rowNumber() over (parti<caret>))
''', '''
from x in [1]
select (rowNumber() over (partitionby ))
''')
  }

  void testCompleteInOver3() {
    completeGinq('''
from x in [1]
select (rowNumber() over (partitionby x orderb<caret>))
''', '''
from x in [1]
select (rowNumber() over (partitionby x orderby ))
''')
  }

  void testCompleteInOver4() {
    completeGinq('''
from x in [1]
select (rowNumber() over (partitionby x orderby x ro<caret>))
''', '''
from x in [1]
select (rowNumber() over (partitionby x orderby x rows ))
''')
  }

  void testCompleteInner() {
    completeGinq('''
from nnnn in (from a in [1] innerhashjo<caret> select b)
select nnnn
''', '''
from nnnn in (from a in [1] innerhashjoin x in  on  select b)
select nnnn
''')
  }

  void testCompleteAsc() {
    completeGinq('''
from x in [1]
orderby x in as<caret>
select x
''', '''
from x in [1]
orderby x in asc
select x
''')
  }

  void testCompleteDesc() {
    completeGinq('''
from x in [1]
orderby x in des<caret>
select x
''', '''
from x in [1]
orderby x in desc
select x
''')
  }

  void testCompleteAscNullsfirst() {
    completeGinq('''
from x in [1]
orderby x in asc(nullsf<caret>)
select x
''', '''
from x in [1]
orderby x in asc(nullsfirst)
select x
''')
  }

  void testCompleteDescNullslast() {
    completeGinq('''
from x in [1]
orderby x in desc(nullsla<caret>)
select x
''', '''
from x in [1]
orderby x in desc(nullslast)
select x
''')
  }

  void testMethodJoin() {
    completeMethodGinq('''\
from x in [1] 
fullhashjoi<caret>
select x
''', '''\
from x in [1] 
fullhashjoin x1 in  on
select x
''')
  }

  void testMethodCompleteBindings() {
    completeMethodGinq('''
from xxxx in [1] 
where xxx<caret>
select xxxx
''', '''
from xxxx in [1] 
where xxxx
select xxxx
''')
  }

  void testMethodCompleteInner() {
    completeMethodGinq('''
from nnnn in (from a in [1] innerhashjo<caret> select b)
select nnnn
''', '''
from nnnn in (from a in [1] innerhashjoin x in  on  select b)
select nnnn
''')
  }

  void testShutdown() {
    doVariantableTest('''
GQ {
  shut<caret>
}
''', '', CompletionType.BASIC, 'shutdown ', 'addShutdownHook')
  }

  void testImmediate() {
    completeGinq('''
shutdown immedi<caret>
''', '''
shutdown immediate
''')
  }

  void testAbort() {
    completeGinq('''
shutdown abor<caret>
''', '''
shutdown abort
''')
  }

}
