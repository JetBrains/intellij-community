// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.confusing.UnnecessaryQualifiedReferenceInspection
import org.jetbrains.plugins.groovy.util.HighlightingTest

@CompileStatic
class GroovyHighlighting40Test extends LightGroovyTestCase implements HighlightingTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_4_0

  void 'test permits without sealed'() {
    myFixture.addClass("""
public interface Bar {
    static void foo() {
        System.out.println("20");
    }
}
""")
    myFixture.enableInspections(UnnecessaryQualifiedReferenceInspection)
    highlightingTest '''
class A implements Bar {
    void bar() {
        Bar.foo()
    }
}
'''
  }

  void 'test var in for loop'() {
    highlightingTest '''
for (var i : []) {}
'''
  }

}
