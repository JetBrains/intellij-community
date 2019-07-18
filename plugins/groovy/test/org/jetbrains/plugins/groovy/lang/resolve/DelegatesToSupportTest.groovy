// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

@CompileStatic
class DelegatesToSupportTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.enableInspections(GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection)
  }

  void 'test extension method genericTypeIndex'() {
    fixture.with {
      addFileToProject 'xt/MyExtensionModule.groovy', '''\
package xt

class MyExtensionModule {
    static <T> void foo(String s, @DelegatesTo.Target Class<T> clazz, @DelegatesTo(genericTypeIndex = 0) Closure c) {}
}
'''
      addFileToProject 'META-INF/services/org.codehaus.groovy.runtime.ExtensionModule', '''\
extensionClasses=xt.MyExtensionModule
'''
      configureByText 'a.groovy', '''\
class MyDelegate {
    def prop
    def bar() {}
}

"s".foo(MyDelegate) {
    prop
    bar()
    <caret>
}
'''
      checkHighlighting()
      completeBasic()
      lookupElementStrings.with {
        assert contains("prop")
        assert contains("bar")
      }
    }
  }

  void 'test extension method generic type'() {
    fixture.with {
      addClass '''\
package test;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public class TestExtension {
    public static <T> T letIf(T obj, boolean cond, @DelegatesTo(type = "T", strategy = Closure.DELEGATE_FIRST) Closure<T> closure) {}
}
'''
      addFileToProject 'META-INF/services/org.codehaus.groovy.runtime.ExtensionModule', 'extensionClasses=test.TestExtension'
      configureByText 'a.groovy', '''\
@groovy.transform.CompileStatic
def foo(String s) {
    s.letIf(true) {
        concat ""
    }
    s.letIf(false) {
        delegate.concat ""
    }
}
'''
      checkHighlighting()
    }
  }
}
