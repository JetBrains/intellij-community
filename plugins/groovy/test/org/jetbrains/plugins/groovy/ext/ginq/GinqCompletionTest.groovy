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
    doBasicTest("GQ { \n $before \n }", "GQ { \n $after \n }")
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

}
