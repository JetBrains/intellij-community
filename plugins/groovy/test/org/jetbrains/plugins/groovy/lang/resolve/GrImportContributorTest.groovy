// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.resolve.imports.*

@CompileStatic
class GrImportContributorTest extends LightGroovyTestCase {

  LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.enableInspections(GrUnresolvedAccessInspection)
    fixture.addClass '''
package foo.bar;
public class MyClass {
  public static Object BAR = null;
  public static void foo() {}
  public void hello() {}
}
'''
  }

  void 'test regular import'() {
    GrImportContributor.EP_NAME.getPoint().registerExtension({
      [new RegularImport("foo.bar.MyClass")]
    } as GrImportContributor, myFixture.testRootDisposable)
    fixture.with {
      configureByText('a.groovy', 'new MyClass()')
      checkHighlighting()
    }
  }

  void 'test static import method'() {
    GrImportContributor.EP_NAME.getPoint().registerExtension({
      [new StaticImport("foo.bar.MyClass", "foo")]
    } as GrImportContributor, myFixture.testRootDisposable)
    fixture.with {
      configureByText('a.groovy', 'foo()')
      checkHighlighting()
    }
  }

  void 'test static import field'() {
    GrImportContributor.EP_NAME.getPoint().registerExtension({
      [new StaticImport("foo.bar.MyClass", "BAR")]
    } as GrImportContributor, myFixture.testRootDisposable)
    fixture.with {
      configureByText('a.groovy', 'println BAR')
      checkHighlighting()
    }
  }

  void 'test star import'() {
    GrImportContributor.EP_NAME.getPoint().registerExtension({
      [new StarImport("foo.bar")]
    } as GrImportContributor, myFixture.testRootDisposable)
    fixture.with {
      configureByText('a.groovy', 'new MyClass()')
      checkHighlighting()
    }
  }

  void 'test static star import'() {
    GrImportContributor.EP_NAME.getPoint().registerExtension({
      [new StaticStarImport("foo.bar.MyClass")]
    } as GrImportContributor, myFixture.testRootDisposable)
    fixture.with {
      configureByText('a.groovy', '''\
println foo()
println <warning>hello</warning>()
println BAR''')
      checkHighlighting()
    }
  }
}
