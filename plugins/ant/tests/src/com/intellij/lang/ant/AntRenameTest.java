package com.intellij.lang.ant;

import com.intellij.lang.ant.psi.AntFile;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
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

  protected String getTestDataPath() {
    return PathManager.getHomePath().replace('\\', '/') + "/plugins/ant/tests/data/psi/rename/";
  }

  private void doTest() throws Exception {
    final String filename = getTestName(true) + ".xml";
    VirtualFile vfile = VirtualFileManager.getInstance().findFileByUrl("file://" + getTestDataPath() + filename);
    String text = FileDocumentManager.getInstance().getDocument(vfile).getText();
    int off = text.indexOf("<ren>");
    text = text.replace("<ren>", "");
    configureFromFileText(filename, text);
    myFile = myFile.getViewProvider().getPsi(AntSupport.getLanguage());
    assertNotNull(myFile);
    assertTrue(myFile instanceof AntFile);
    PsiElement element = myFile.findElementAt(off);
    final PsiReference[] refs = element.getReferences();
    if (refs.length > 0) {
      int i = 0;
      element = refs[0].resolve();
      while (element != null && !text.substring(off).trim().startsWith(((PsiNamedElement)element).getName())) {
        element = refs[++i].resolve();
      }
    }
    else {
      element = element.getParent();
    }
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
