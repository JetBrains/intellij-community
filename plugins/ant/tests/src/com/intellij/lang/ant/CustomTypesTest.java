package com.intellij.lang.ant;

import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class CustomTypesTest extends ParsingTestCase {

  private static final String myCustomTaskClass = "com.intellij.lang.ant.typedefs.AntCustomTask";

  public CustomTypesTest() {
    super("", "ant");
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/ant/tests/data/psi/customTypes";
  }

  public void testAntCustomTask() throws Exception {
    doTest();
  }

  public void testAntCustomTaskWithClasspath() throws Exception {
    doTest();
  }

  public void testAntCustomTaskWithComplexClasspath() throws Exception {
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
    final AntTypeDefinition result = antFile.getBaseTypeDefinition(myCustomTaskClass);
    assertNotNull(result);
    return result;
  }

  protected String loadFile(String name) throws Exception {
    String fullName = getTestDataPath() + File.separatorChar + name;
    String text = new String(FileUtil.loadFileText(new File(fullName))).trim();
    text = StringUtil.convertLineSeparators(text);
    return text;
  }
}
