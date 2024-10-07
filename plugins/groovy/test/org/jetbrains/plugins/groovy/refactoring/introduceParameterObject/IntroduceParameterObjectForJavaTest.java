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
package org.jetbrains.plugins.groovy.refactoring.introduceParameterObject

import com.intellij.psi.PsiMethod
import com.intellij.refactoring.changeSignature.JavaMethodDescriptor
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectProcessor
import com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectClassDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class IntroduceParameterObjectForJavaTest extends LightJavaCodeInsightFixtureTestCase {
  final String basePath = TestUtils.testDataPath + "/refactoring/introduceParameterObjectForJava/"

  void testSimple() throws Exception {
    doTest()
  }

  protected void doTest() {
    def psiClass = myFixture.addClass("class MyTest {\n" +
                                      "  void foo(String a, String b) {}\n" +
                                      "}")
    final PsiMethod method = psiClass.findMethodsByName("foo", false)[0]
    assertNotNull method

    myFixture.configureByFile(getTestName(true) + ".groovy")

    def infos = new JavaMethodDescriptor(method).getParameters()

    final JavaIntroduceParameterObjectClassDescriptor classDescriptor =
      new JavaIntroduceParameterObjectClassDescriptor("Param", "", null, false, true, null, infos.toArray(new ParameterInfoImpl[0]), method,
                                                      false)
    final List<ParameterInfoImpl> parameters = new JavaMethodDescriptor(method).getParameters()
    IntroduceParameterObjectProcessor processor =
      new IntroduceParameterObjectProcessor<PsiMethod, ParameterInfoImpl, JavaIntroduceParameterObjectClassDescriptor>(
        method, classDescriptor,
        parameters,
        false)
    processor.run()

    myFixture.checkResultByFile(getTestName(true) + "_after.groovy")
  }
}
