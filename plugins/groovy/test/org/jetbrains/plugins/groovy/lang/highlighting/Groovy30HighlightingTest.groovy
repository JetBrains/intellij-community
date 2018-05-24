// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor

@CompileStatic
class Groovy30HighlightingTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_3_0
  final String basePath = super.basePath + 'v30/'

  void 'test default method in interfaces'() {
    testHighlighting '''
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
    testHighlighting '''
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
    testHighlighting '''
interface I {
    int foo() 
    default int bar() {
        2
    }
}

I i = {3}
'''
  }

  void 'test identity operators'() {
    fixture.testHighlighting "${testName}.groovy"
  }

  void 'test elvis assignment'() {
    fixture.testHighlighting testName + '.groovy'
  }

  void 'test safe index access'() {
    fixture.testHighlighting testName + '.groovy'
  }
}
