// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * Created by Max Medvedev on 04/02/14
 */
public class StringExtractingTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/stringExtracting/";
  }

  public void testStringExtractingFromQuote() { doTest(); }

  public void testStringExtractingFromDoubleQuotes() { doTest(); }

  public void testStringExtractingFromSlashyString() { doTest(); }

  public void testStringExtractingFromDollarSlashyString() { doTest(); }

  public void testSlashyWithSlash() { doTest(); }

  public void testDollarSlashyWithDollar() { doTest(); }

  public void testSlashyWithSlashInsideExtractedPart() { doTest(); }

  private void doTest() {
    final GroovyFile file = (GroovyFile)myFixture.configureByFile(getTestName(true) + ".groovy");
    final SelectionModel model = myFixture.getEditor().getSelectionModel();
    final TextRange range = new TextRange(model.getSelectionStart(), model.getSelectionEnd());

    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        new StringPartInfo((GrLiteral)PsiUtil.skipParentheses(file.getStatements()[0], false), range).replaceLiteralWithConcatenation(null);
        model.removeSelection();
      });
    }, null, null);

    myFixture.checkResultByFile(getTestName(true) + "_after.groovy");
  }
}
