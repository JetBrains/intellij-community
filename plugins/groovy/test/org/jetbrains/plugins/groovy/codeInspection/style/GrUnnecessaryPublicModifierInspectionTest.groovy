// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
class GrUnnecessaryPublicModifierInspectionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  void 'test highlighting and fix'() {
    fixture.with {
      enableInspections GrUnnecessaryPublicModifierInspection
      configureByText '_.groovy', '''\
<warning descr="Modifier 'public' is not necessary">pu<caret>blic</warning> class A {

    <warning descr="Modifier 'public' is not necessary">public</warning> A() {}

    <warning descr="Modifier 'public' is not necessary">public</warning> foo() {}

    public x

    <warning descr="Modifier 'public' is not necessary">public</warning> class B {}
}

<warning descr="Modifier 'public' is not necessary">public</warning> enum E {}

<warning descr="Modifier 'public' is not necessary">public</warning> interface I {}
'''
      checkHighlighting()
      launchAction findSingleIntention("Fix all 'Unnecessary 'public''")
      checkResult '''\
class A {

    A() {}

    def foo() {}

    public x

    class B {}
}

enum E {}

interface I {}
'''
    }
  }
}