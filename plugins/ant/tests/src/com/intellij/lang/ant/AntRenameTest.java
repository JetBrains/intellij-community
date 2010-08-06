/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class AntRenameTest extends LightCodeInsightTestCase {

  public void testSimpleProperty() throws Exception {
    doTest();
  }

  public void testSimplePropertyReference() throws Exception {
    doTest();
  }

  public void testParam() throws Exception {
    doTest();
  }

  public void testParamReference() throws Exception {
    doTest();
  }

  public void testRefid() throws Exception {
    doTest();
  }

  public void testRefidReference() throws Exception {
    doTest();
  }

  public void testRefidReferenceInDependieTarget() throws Exception {
    doTest();
  }

  public void testSingleTarget() throws Exception {
    doTest();
  }

  public void testSingleTargetReference() throws Exception {
    doTest();
  }

  public void testAntCall() throws Exception {
    doTest();
  }

  public void testAntCallReference() throws Exception {
    doTest();
  }

  public void testDependsTarget1() throws Exception {
    doTest();
  }

  public void testDependsTarget2() throws Exception {
    doTest();
  }

  public void testDependsTargetReference1() throws Exception {
    doTest();
  }

  public void testDependsTargetReference2() throws Exception {
    doTest();
  }

  public void testTargetProperties() throws Exception {
    doTest();
  }

  public void testTstampProperty() throws Exception {
    doTest();
  }

  public void testTstampProperty1() throws Exception {
    doTest();
  }

  /*
  [jeka: behaviour changed - no property definitions in target's if/unless attributes anymore]
  
  public void testTargetIfProperty() throws Exception {
    doTest();
  }

  public void testTargetUnlessProperty() throws Exception {
    doTest();
  }
  */

  public void testInputProperty() throws Exception {
    doTest();
  }

  public void testInputProperty1() throws Exception {
    doTest();
  }

  public void testRenameByRef() throws Exception {
    doTest();
  }

  public void testRenameByDeclaration() throws Exception {
    doTest();
  }

  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant").replace('\\', '/') + "/tests/data/psi/rename/";
  }

  private void doTest() throws Exception {
    final String filename = getTestName(true) + ".xml";
    VirtualFile vfile = VirtualFileManager.getInstance().findFileByUrl("file://" + getTestDataPath() + filename);
    String text = FileDocumentManager.getInstance().getDocument(vfile).getText();
    final int off = text.indexOf("<ren>");
    text = text.replace("<ren>", "");
    configureFromFileText(filename, text);
    assertNotNull(myFile);
    PsiElement element = TargetElementUtilBase.getInstance().findTargetElement(
      getEditor(), 
      TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtilBase.ELEMENT_NAME_ACCEPTED,
      off);
    assertNotNull(element);
    assertTrue(element instanceof PsiNamedElement);
    final RenameRefactoring rename =
      RefactoringFactory.getInstance(getProject()).createRename(element, ((PsiNamedElement)element).getName() + "-after");
    rename.setSearchInComments(false);
    rename.setSearchInNonJavaFiles(false);
    rename.run();
    checkResultByFile(filename + "-after");
  }
}
