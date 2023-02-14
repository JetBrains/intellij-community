// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase

class GrIntentions40Test extends GrIntentionTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_4_0

  void testQualifyCall() {
    myFixture.addClass("""
public interface Bar {
    static void foo() {
        System.out.println("20");
    }
}
""")
    doTextTest('''
class A implements Bar {
    void bar() {
        f<caret>oo()
    }
}
''', 'Replace with','''
class A implements Bar {
    void bar() {
        Bar.foo()
    }
}
''')
  }
}
