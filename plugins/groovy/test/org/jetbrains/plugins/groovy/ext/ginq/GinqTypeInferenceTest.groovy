// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceTestBase

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING

class GinqTypeInferenceTest extends TypeInferenceTestBase {

  final LightProjectDescriptor projectDescriptor = GinqTestUtils.projectDescriptor

  @Override
  void setUp() throws Exception {
    super.setUp()
    myFixture.enableInspections(GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection)
  }

  void testGQL() {
    doTest """
def ee = GQL {
        from a in [1]
        select a
    }
e<caret>e
""", "java.util.List<java.lang.Integer>"
  }

  void testNamedRecord() {
    doTest """
def ee = GQL {
        from a in [1]
        select a, a
    }
e<caret>e
""", "java.util.List<org.apache.groovy.ginq.provider.collection.runtime.NamedRecord>"
  }

  void testActualBinding() {
    doTest """
def ee = GQL {
        from aa in [1]
        select aa as e1, aa as e2
    }
ee[0].e<caret>1
""", JAVA_LANG_INTEGER
  }

  void testDataSourceBinding() {
    doTest """
def ee = GQL {
        from aa in [1]
        select aa as e1, aa as e2
    }
ee[0].a<caret>a
""", JAVA_LANG_INTEGER
  }

  void testJoinSourceBinding() {
    doTest """
def ee = GQL {
        from aa in [1]
        crossjoin bb in [""]
        select aa as e1, bb as e2
    }
ee[0].b<caret>b
""", JAVA_LANG_STRING
  }

  void testGetAt1() {
    doTest """
def ee = GQL {
        from aa in [1]
        crossjoin bb in [""]
        select aa as e1, bb as e2
    }
ee[0].ge<caret>t(0)
""", JAVA_LANG_INTEGER
  }

  void testGetAt2() {
    doTest """
def ee = GQL {
        from aa in [1]
        crossjoin bb in [""]
        select aa as e1, bb as e2
    }
ee[0].ge<caret>t(1)
""", JAVA_LANG_STRING
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

  void testPrimitive() {
    doTest """
def aa = GQL {
        from x in [1]
        select x.byteValue()
    }
a<caret>a""", "java.util.List<java.lang.Byte>"
  }

  void testOver() {
    doTest """
def aa = GQL {
        from nnnn in [3]
        select (rowNumber() over(orderby nnnn))
    }
a<caret>a""", "java.util.List<java.lang.Long>"
  }

  void testMethodNestedGinq() {
    doTest """
import groovy.ginq.transform.GQ

@GQ
def foo() {
  from v in (
    from nn in [1, 2, 3]
    select nn, Math.pow(n, 2) as powerOfN
  )
  select v.n<caret>n, v.powerOfN
}""", JAVA_LANG_INTEGER
  }

  void testMethodReturnType1() {
    doTest """
import groovy.ginq.transform.GQ

@GQ
def foo() {
  from v in (
    from nn in [1, 2, 3]
    select nn, Math.pow(n, 2) as powerOfN
  )
  select v.nn, v.powerOfN
}

def zz = foo()
z<caret>z""", "org.apache.groovy.ginq.provider.collection.runtime.Queryable<org.apache.groovy.ginq.provider.collection.runtime.NamedRecord>"
  }

  void testMethodReturnType2() {
    doTest """
import groovy.ginq.transform.GQ

@GQ(List)
def foo() {
  from v in (
    from nn in [1, 2, 3]
    select nn, Math.pow(n, 2) as powerOfN
  )
  select v.nn, v.powerOfN
}

def zz = foo()
z<caret>z""", "java.util.List<org.apache.groovy.ginq.provider.collection.runtime.NamedRecord>"
  }

  void testMethodReturnType3() {
    doTest """
import groovy.ginq.transform.GQ

@GQ(List)
def foo() {
  from v in (
    from nn in [1, 2, 3]
    select nn, Math.pow(n, 2) as powerOfN
  )
  select v.nn as rr, v.powerOfN
}

def zz = foo()
zz[0].r<caret>r""", JAVA_LANG_INTEGER
  }
}
