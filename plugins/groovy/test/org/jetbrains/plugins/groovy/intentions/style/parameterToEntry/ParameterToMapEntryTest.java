// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.parameterToEntry;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterFixture;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterFixtureKt.groovyFormatterFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ParameterToMapEntryTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_1_7);
  private final TestFixture<GroovyFormatterFixture> formatter = groovyFormatterFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    formatter.get().getFixture().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "paramToMap/" + formatter.get().getFixture().getTestName(true) + "/");
  }


  /*
  @Override
  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject());
  }
  */

  @Test
  public void testParam1() {
    doTestImpl("A.groovy");
  }

  @Test
  public void testFormatter() {
    doTestImpl("A.groovy");
  }

  @Test
  public void testClosureAtEnd() {
    doTestImpl("A.groovy");
  }

  @Test
  public void testClosure1() {
    doTestImpl("A.groovy");
  }

  @Test
  public void testNewMap() {
    doTestImpl("A.groovy");
  }

  @Test
  public void testTestError() {
    doTestImpl("A.groovy");
  }

  @Test
  public void testSecondClosure() {
    doTestImpl("A.groovy");
  }

  @Test
  public void testVarArgs() {
    doTestImpl("A.groovy");
  }

  @Test
  public void testCallMethod() {
    doTestImpl("A.groovy");
  }

  @Test
  public void testGettersAndCallMethod() {
    doTestImpl("A.groovy");
  }

  private void doTestImpl(String filePath) {
    LightJavaCodeInsightTestFixture myFixture = formatter.get().getFixture();
    myFixture.configureByFile(filePath);
    EdtTestUtil.runInEdtAndWait(() -> {
      int offset = myFixture.getEditor().getCaretModel().getOffset();
      final PsiFile file = myFixture.getFile();

      final ConvertParameterToMapEntryIntention intention = new ConvertParameterToMapEntryIntention();
      PsiElement element = file.findElementAt(offset);
      while (element != null && !(element instanceof GrReferenceExpression || element instanceof GrParameter)) {
        element = element.getParent();
      }
      assertNotNull(element);

      final PsiElementPredicate condition = intention.getElementPredicate();
      assertTrue(condition.satisfiedBy(element));

      // Launch it!
      intention.processIntention(element, myFixture.getProject(), myFixture.getEditor());
      PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
      final String result = file.getText();
      //System.out.println(result);
      myFixture.checkResultByFile(filePath.replace(".groovy", ".test"), true);
    });
//    String expected = getExpectedResult(filePath);
//    Assert.assertEquals(expected, result);
  }

  private String getExpectedResult(final String filePath) {
    LightJavaCodeInsightTestFixture myFixture = formatter.get().getFixture();
    assertTrue(filePath.endsWith(".groovy"));
    String testFilePath = StringUtil.trimEnd(filePath, "groovy") + "test";

    final File file = new File(myFixture.getTestDataPath() + testFilePath);
    assertTrue(file.exists());
    String expected = "";

    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      String line = reader.readLine();
      while (line != null) {
        expected += line;
        line = reader.readLine();
        if (line != null) expected += "\n";
      }
      reader.close();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return expected;
  }

}