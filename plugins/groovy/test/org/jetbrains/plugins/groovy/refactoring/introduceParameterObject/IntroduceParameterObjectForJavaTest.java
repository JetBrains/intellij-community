// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceParameterObject;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.changeSignature.JavaMethodDescriptor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectProcessor;
import com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectClassDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class IntroduceParameterObjectForJavaTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSimple() throws Exception {
    doTest();
  }

  protected void doTest() {
    PsiClass psiClass = myFixture.addClass("""
                                             class MyTest {
                                               void foo(String a, String b) {}
                                             }""");
    final PsiMethod method = psiClass.findMethodsByName("foo", false)[0];
    TestCase.assertNotNull(method);

    myFixture.configureByFile(getTestName(true) + ".groovy");

    List<ParameterInfoImpl> infos = new JavaMethodDescriptor(method).getParameters();

    final JavaIntroduceParameterObjectClassDescriptor classDescriptor =
      new JavaIntroduceParameterObjectClassDescriptor("Param", "", null, false, true, null, infos.toArray(new ParameterInfoImpl[0]), method,
                                                      false);
    final List<ParameterInfoImpl> parameters = new JavaMethodDescriptor(method).getParameters();
    IntroduceParameterObjectProcessor<PsiMethod, ParameterInfoImpl, JavaIntroduceParameterObjectClassDescriptor> processor =
      new IntroduceParameterObjectProcessor<>(method,
                                              classDescriptor,
                                              parameters, false);
    processor.run();

    myFixture.checkResultByFile(getTestName(true) + "_after.groovy");
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "/refactoring/introduceParameterObjectForJava/";
  }
}
