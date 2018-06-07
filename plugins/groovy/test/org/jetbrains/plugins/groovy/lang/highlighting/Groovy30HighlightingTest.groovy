// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.GroovyVersionBasedTest
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class Groovy30HighlightingTest extends GroovyVersionBasedTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0
  final String basePath = TestUtils.testDataPath + 'highlighting/v30/'

  void 'test default method in interfaces'() {
    highlightingTest '''
import groovy.transform.CompileStatic

interface I {
    default int bar() {
        2
    }
}

@CompileStatic
interface I2 {
    default int bar() {
        2
    }
}
'''
  }

  void 'test default modifier'() {
    highlightingTest '''
default interface I {
}

trait T {
    <warning descr="Modifier 'default' makes sense only in interface's methods">default</warning> int bar() {
        2
    }
}

class C {
    <warning descr="Modifier 'default' makes sense only in interface's methods">default</warning> int bar() {
        2
    }
}
'''
  }

  void 'test sam with default modifier'() {
    highlightingTest '''
interface I {
    int foo() 
    default int bar() {
        2
    }
}

I i = {3}
'''
  }
}
