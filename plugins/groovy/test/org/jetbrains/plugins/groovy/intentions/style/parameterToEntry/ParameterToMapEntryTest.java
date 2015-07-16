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
package org.jetbrains.plugins.groovy.intentions.style.parameterToEntry;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.*;

/**
 * @author ilyas
 */
public class ParameterToMapEntryTest extends GroovyFormatterTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "paramToMap/" + getTestName(true) + "/";
  }

  public static final DefaultLightProjectDescriptor GROOVY_17_PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY").getModifiableModel();
      final VirtualFile groovyJar = JarFileSystem.getInstance().refreshAndFindFileByPath(TestUtils.getMockGroovy1_7LibraryName()+"!/");
      assert groovyJar != null;
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
      modifiableModel.commit();
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GROOVY_17_PROJECT_DESCRIPTOR;
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
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return expected;
  }

}