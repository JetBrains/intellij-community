package com.intellij.htmltools.navigation;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class NotClosedAttributeReferenceTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);

  @Test
  public void testXHtmlCompletion() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("test.html", "<p id=<caret>\"");
    try {
      myFixture.getReferenceAtCaretPosition();
    } catch (Exception e) {
      fail();
    }
  }
}
