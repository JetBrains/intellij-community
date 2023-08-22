// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.confusing.ClashingTraitMethodsInspection
/**
 * Created by Max Medvedev on 09/06/14
 */
class ClashingTraitMethodsQuickFixTest /*extends GrIntentionTestCase*/ {
  ClashingTraitMethodsQuickFixTest() {
    super(GroovyBundle.message("declare.explicit.implementations.of.trait"), ClashingTraitMethodsInspection)
  }

  void testQuickFix() {
    doTextTest('''\
trait T1 {
    def foo(){}
}

trait T2 {
    def foo(){}
}

class <caret>A implements T1, T2 {

}
''', '''\
trait T1 {
    def foo(){}
}

trait T2 {
    def foo(){}
}

class A implements T1, T2 {

    @Override
    def foo() {
        return T2.super.foo()
    }
}
''')
  }
}
