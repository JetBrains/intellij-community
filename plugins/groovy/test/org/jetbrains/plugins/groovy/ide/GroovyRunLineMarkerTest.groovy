// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ide

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
class GroovyRunLineMarkerTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  @Override
  void setUp() {
    super.setUp()
    Registry.get("ide.jvm.run.marker").setValue(true, testRootDisposable)
  }

  void 'test simple'() {
    fixture.configureByText '_.groovy', '''\
class MainTest {
  static void <caret>foo(String[] args) {}
  static void main(String[] args) {}
}
'''
    assert fixture.findGuttersAtCaret().isEmpty()
    assert fixture.findAllGutters().size() == 2
  }

  void 'test implicit String array'() {
    fixture.configureByText '_.groovy', '''\
class MainTest {
  static void main(args) {}
}
'''
    assert fixture.findAllGutters().size() == 2
  }

  void 'test Java class inheritor'() {
    fixture.addClass '''\
class Main {
  public static void main(String[] args){}
}
'''
    fixture.configureByText '_.groovy', '''\
class MainTest extends Main {}
'''
    assert fixture.findAllGutters().size() == 1
  }

  void 'test default parameters'() {
    fixture.configureByText '_.groovy', '''\
class MainTest {
  static void main(String[] args, b = 2) {}
}
'''
    assert fixture.findAllGutters().size() == 2
  }
}
