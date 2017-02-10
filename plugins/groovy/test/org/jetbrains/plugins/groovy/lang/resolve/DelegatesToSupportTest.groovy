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
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

@CompileStatic
class DelegatesToSupportTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

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
}
