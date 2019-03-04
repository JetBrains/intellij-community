// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.parameterToEntry;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.LightProjectDescriptor;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author ilyas
 */
public class ParameterToMapEntryTest extends GroovyFormatterTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "paramToMap/" + getTestName(true) + "/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_1_7;
  }  

  /*
  @Override
  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject());
  }
  */

  public void testParam1() {
    doTestImpl("A.groovy");
  }

  public void testFormatter() {
    doTestImpl("A.groovy");
  }

  public void testClosureAtEnd() {
    doTestImpl("A.groovy");
  }

  public void testClosure1() {
    doTestImpl("A.groovy");
  }

  public void testNewMap() {
    doTestImpl("A.groovy");
  }

  public void testTestError() {
    doTestImpl("A.groovy");
  }

  public void testSecondClosure() {
    doTestImpl("A.groovy");
  }

  public void testVarArgs() {
    doTestImpl("A.groovy");
  }

  public void testCallMethod() {
    doTestImpl("A.groovy");
  }

  public void testGettersAndCallMethod() {
    doTestImpl("A.groovy");
  }

  private void doTestImpl(String filePath) {
    myFixture.configureByFile(filePath);
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    final PsiFile file = myFixture.getFile();


    final ConvertParameterToMapEntryIntention intention = new ConvertParameterToMapEntryIntention();
    PsiElement element = file.findElementAt(offset);
    while (element != null && !(element instanceof GrReferenceExpression || element instanceof GrParameter)) {
      element = element.getParent();
    }
    Assert.assertNotNull(element);

    final PsiElementPredicate condition = intention.getElementPredicate();
    Assert.assertTrue(condition.satisfiedBy(element));

    // Launch it!
    intention.processIntention(element, myFixture.getProject(), myFixture.getEditor());
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    final String result = file.getText();
    //System.out.println(result);
    myFixture.checkResultByFile(filePath.replace(".groovy", ".test"), true);
//    String expected = getExpectedResult(filePath);
//    Assert.assertEquals(expected, result);
  }

  private String getExpectedResult(final String filePath) {
    Assert.assertTrue(filePath.endsWith(".groovy"));
    String testFilePath = StringUtil.trimEnd(filePath, "groovy") + "test";

    final File file = new File(getTestDataPath() + testFilePath);
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