package com.intellij.refactoring;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;

@TestApplication
public class MoveHtmlFileTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);

  @Test
  public void testHtml() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    EdtTestUtil.runInEdtAndWait(() -> {
      var targetDir = WriteAction.compute(() -> myFixture.getFile().getContainingDirectory().createSubdirectory("toDir"));
      new MoveFilesOrDirectoriesProcessor(
        myFixture.getProject(), new PsiElement[]{b}, targetDir, false, false, null, null
      ).run();
    });
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
