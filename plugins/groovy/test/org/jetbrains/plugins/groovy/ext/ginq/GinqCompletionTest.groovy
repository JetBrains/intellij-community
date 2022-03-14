// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.completion.GroovyCompletionTestBase

class GinqCompletionTest extends GroovyCompletionTestBase {

  final LightProjectDescriptor projectDescriptor = GinqTestUtils.projectDescriptor

  @Override
  void setUp() throws Exception {
    super.setUp()
    GinqTestUtils.setUp(myFixture)
    myFixture.enableInspections(GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection)
  }

  private void completeGinq(String before, String after) {
    doBasicTest("GQ {\n$before\n}", "GQ {\n$after\n}")
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
sele<caret>
''', '''\
from x in [1]
select
''')
  }

  void testJoin() {
    completeGinq('''\
from x in [1] 
fullhashjoi<caret>
select x
''', '''\
from x in [1] 
fullhashjoin x in  on
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

}
