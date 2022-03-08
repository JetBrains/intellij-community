// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceTestBase

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER

class GinqTypeInferenceTest extends TypeInferenceTestBase {

  final LightProjectDescriptor projectDescriptor = GinqTestUtils.projectDescriptor

  @Override
  void setUp() throws Exception {
    super.setUp()
    GinqTestUtils.setUp(fixture)
    myFixture.enableInspections(GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection)
  }

  void testNestedGinq() {
    doTest """
GQ {
  from v in (
    from nn in [1, 2, 3]
    select nn, Math.pow(n, 2) as powerOfN
  )
  select v.n<caret>n, v.powerOfN
}""", JAVA_LANG_INTEGER
  }
}
