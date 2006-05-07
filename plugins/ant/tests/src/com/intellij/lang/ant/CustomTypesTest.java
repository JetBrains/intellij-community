package com.intellij.lang.ant;

import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.annotations.NotNull;

public class CustomTypesTest extends ParsingTestCase {

  public CustomTypesTest() {
    super("", "ant");
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/ant/tests/data";
  }

  public void testAntCustomTask() throws Exception {
    doTest();
  }

  @NotNull
  protected AntTypeDefinition doTest() throws Exception {
    String name = getTestName(false);
    String text = loadFile(name + "." + myFileExt);
    PsiFile file = createFile(name + "." + myFileExt, text);
    final AntFile antFile = (AntFile) file.getViewProvider().getPsi(AntSupport.getLanguage());
    antFile.accept(new PsiRecursiveElementVisitor() {
      public void visitElement(PsiElement element) {
        super.visitElement(element);
      }

      public void visitReferenceExpression(PsiReferenceExpression expression) {
      }
    });
    final AntTypeDefinition result = antFile.getBaseTypeDefinition("com.intellij.lang.ant.typedefs." + name);
    assertNotNull(result);
    return result;
  }
}
