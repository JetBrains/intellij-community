/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod

import static org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor.GROOVY_2_1
import static org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor.GROOVY_2_3_9

abstract class AbstractResolveDGMMethodTest extends GroovyResolveTestCase {

  final String basePath = "resolve/dgm"

  abstract Map<String, String> getData()

  void testIsNumber() {
    def resolved = resolveByText('"1.2.3".isN<caret>umber()')
    def method = resolved as GrGdkMethod
    assertEquals(getData()[getTestName(true)], method.staticMethod.containingClass.qualifiedName)
  }

  void testCollectionSort() {
    def resolved = resolveByText('[].so<caret>rt()', GrGdkMethod)
    def parameterList = resolved.staticMethod.parameterList.parameters
    assertSize(1, parameterList)
    def qualifierParam = parameterList[0]
    assertEquals(getData()[getTestName(true)], qualifierParam.type.canonicalText)
  }

  static class ResolveDGMMethod21Test extends AbstractResolveDGMMethodTest {
    LightProjectDescriptor projectDescriptor = GROOVY_2_1
    Map<String, String> data = [
      isNumber      : "org.codehaus.groovy.runtime.StringGroovyMethods",
      collectionSort: "java.util.Collection<T>"
    ]
  }

  static class ResolveDGMMethod239Test extends AbstractResolveDGMMethodTest {
    LightProjectDescriptor projectDescriptor = GROOVY_2_3_9
    Map<String, String> data = [
      isNumber      : "org.codehaus.groovy.runtime.StringGroovyMethods",
      collectionSort: "java.lang.Iterable<T>"
    ]
  }
}
