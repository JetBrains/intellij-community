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

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class AntRenameTest extends LightCodeInsightTestCase {

  public void testSimpleProperty() {
    doTest();
  }

  public void testSimplePropertyReference() {
    doTest();
  }

  public void testParam() {
    doTest();
  }

  public void testParamReference() {
    doTest();
  }

  public void testRefid() {
    doTest();
  }

  public void testRefidReference() {
    doTest();
  }

  public void testRefidReferenceInDependieTarget() {
    doTest();
  }

  public void testSingleTarget() {
    doTest();
  }

  public void testSingleTargetReference() {
    doTest();
  }

  public void testAntCall() {
    doTest();
  }

  public void testAntCallReference() {
    doTest();
  }

  public void testDependsTarget1() {
    doTest();
  }

  public void testDependsTarget2() {
    doTest();
  }

  public void testDependsTargetReference1() {
    doTest();
  }

  public void testDependsTargetReference2() {
    doTest();
  }

  public void testTargetProperties() {
    doTest();
  }

  public void testTstampProperty() {
    doTest();
  }

  public void testTstampProperty1() {
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

  public void testInputProperty() {
    doTest();
  }

  public void testInputProperty1() {
    doTest();
  }

  public void testRenameByRef() {
    doTest();
  }

  public void testRenameByDeclaration() {
    doTest();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant").replace('\\', '/') + "/tests/data/psi/rename/";
  }

  private void doTest() {
    final String filename = getTestName(true) + ".xml";
    VirtualFile vfile = VirtualFileManager.getInstance().findFileByUrl("file://" + getTestDataPath() + filename);
    String text = FileDocumentManager.getInstance().getDocument(vfile).getText();
    final int off = text.indexOf("<ren>");
    text = text.replace("<ren>", "");
    configureFromFileText(filename, text);
    assertNotNull(myFile);
    PsiElement element = TargetElementUtil.getInstance().findTargetElement(
      getEditor(), 
      TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil.ELEMENT_NAME_ACCEPTED,
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
