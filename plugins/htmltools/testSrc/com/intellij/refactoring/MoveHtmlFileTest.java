package com.intellij.refactoring;

import com.intellij.openapi.application.WriteAction;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class MoveHtmlFileTest extends BasePlatformTestCase {
  public void testHtml() {
   myFixture.configureByText("A.html", """
      <html>
        <head>
        </head>
        <body>
          <a href="B.html"/>
        </body>
      </html>
      """);
    var b = myFixture.addFileToProject("B.html", """
      <a href="A.html">anchor</a>
      """);
    var targetDir = WriteAction.compute(() -> myFixture.getFile().getContainingDirectory().createSubdirectory("toDir"));
    new MoveFilesOrDirectoriesProcessor(
      getProject(), new PsiElement[]{b}, targetDir, false, false, null, null
    ).run();
    myFixture.checkResult("""
      <html>
        <head>
        </head>
        <body>
          <a href="toDir/B.html"/>
        </body>
      </html>
      """);
  }
}
