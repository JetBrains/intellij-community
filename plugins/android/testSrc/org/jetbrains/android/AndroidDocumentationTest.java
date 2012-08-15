package org.jetbrains.android;

import com.intellij.codeInsight.navigation.CtrlMouseHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiReference;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDocumentationTest extends AndroidTestCase {
  private static final String BASE_PATH = "documentation/";

  public void testValueResourceReferenceQuickDoc() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);
    final PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    assert ref != null;
    assertEquals("value resource 'myString' [strings.xml]", CtrlMouseHandler.getInfo(ref.resolve(), ref.getElement()));
  }
}
