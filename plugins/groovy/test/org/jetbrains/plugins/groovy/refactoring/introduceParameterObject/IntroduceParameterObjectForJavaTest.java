// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceParameterObject;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.changeSignature.JavaMethodDescriptor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectProcessor;
import com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectClassDescriptor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class IntroduceParameterObjectForJavaTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "/refactoring/introduceParameterObjectForJava/");
  }

  @Test
  public void testSimple() throws Exception {
    doTest();
  }

  protected void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass psiClass = myFixture.addClass("""
                                             class MyTest {
                                               void foo(String a, String b) {}
                                             }""");
    final PsiMethod method = psiClass.findMethodsByName("foo", false)[0];
    assertNotNull(method);

    myFixture.configureByFile(myFixture.getTestName(true) + ".groovy");

    EdtTestUtil.runInEdtAndWait(() -> {
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

      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.groovy");
    });
  }
}
