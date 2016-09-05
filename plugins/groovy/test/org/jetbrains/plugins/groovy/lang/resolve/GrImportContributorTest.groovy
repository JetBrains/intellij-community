/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

@CompileStatic
class GrImportContributorTest extends LightGroovyTestCase {

  LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

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
    PlatformTestUtil.registerExtension(GrImportContributor.EP_NAME, {
      [new Import("foo.bar.MyClass", ImportType.REGULAR)]
    } as GrImportContributor, testRootDisposable)
    fixture.with {
      configureByText('a.groovy', 'new MyClass()')
      checkHighlighting()
    }
  }

  void 'test static import method'() {
    PlatformTestUtil.registerExtension(GrImportContributor.EP_NAME, {
      [new Import("foo.bar.MyClass.foo", ImportType.STATIC)]
    } as GrImportContributor, testRootDisposable)
    fixture.with {
      configureByText('a.groovy', 'foo()')
      checkHighlighting()
    }
  }

  void 'test static import field'() {
    PlatformTestUtil.registerExtension(GrImportContributor.EP_NAME, {
      [new Import("foo.bar.MyClass.BAR", ImportType.STATIC)]
    } as GrImportContributor, testRootDisposable)
    fixture.with {
      configureByText('a.groovy', 'println BAR')
      checkHighlighting()
    }
  }

  void 'test star import'() {
    PlatformTestUtil.registerExtension(GrImportContributor.EP_NAME, {
      [new Import("foo.bar", ImportType.STAR)]
    } as GrImportContributor, testRootDisposable)
    fixture.with {
      configureByText('a.groovy', 'new MyClass()')
      checkHighlighting()
    }
  }

  void 'test static star import'() {
    PlatformTestUtil.registerExtension(GrImportContributor.EP_NAME, {
      [new Import("foo.bar.MyClass", ImportType.STATIC_STAR)]
    } as GrImportContributor, testRootDisposable)
    fixture.with {
      configureByText('a.groovy', '''\
println foo()
println <warning>hello</warning>()
println BAR''')
      checkHighlighting()
    }
  }
}
