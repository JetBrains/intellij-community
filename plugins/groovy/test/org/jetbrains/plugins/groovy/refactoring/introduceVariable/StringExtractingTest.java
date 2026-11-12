// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

/**
 * Created by Max Medvedev on 04/02/14
 */
@TestApplication
public class StringExtractingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "refactoring/stringExtracting/");

  @Test
  public void testStringExtractingFromQuote() { doTest(); }

  @Test
  public void testStringExtractingFromDoubleQuotes() { doTest(); }

  @Test
  public void testStringExtractingFromSlashyString() { doTest(); }

  @Test
  public void testStringExtractingFromDollarSlashyString() { doTest(); }

  @Test
  public void testSlashyWithSlash() { doTest(); }

  @Test
  public void testDollarSlashyWithDollar() { doTest(); }

  @Test
  public void testSlashyWithSlashInsideExtractedPart() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final GroovyFile file = (GroovyFile)myFixture.configureByFile(myFixture.getTestName(true) + ".groovy");
    EdtTestUtil.runInEdtAndWait(() -> {
      final SelectionModel model = myFixture.getEditor().getSelectionModel();
      final TextRange range = new TextRange(model.getSelectionStart(), model.getSelectionEnd());

      CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> {
        ApplicationManager.getApplication().runWriteAction(() -> {
          new StringPartInfo((GrLiteral)PsiUtil.skipParentheses(file.getStatements()[0], false), range).replaceLiteralWithConcatenation(null);
          model.removeSelection();
        });
      }, null, null);

      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.groovy");
    });
  }
}
