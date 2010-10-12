package org.jetbrains.javafx;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.command.WriteCommandAction;
import org.jetbrains.javafx.refactoring.surround.surrounders.expressions.JavaFxWithAsSurrounder;
import org.jetbrains.javafx.refactoring.surround.surrounders.expressions.JavaFxWithNotInstanceofSurrounder;
import org.jetbrains.javafx.refactoring.surround.surrounders.expressions.JavaFxWithNotSurrounder;
import org.jetbrains.javafx.refactoring.surround.surrounders.expressions.JavaFxWithParenthesesSurrounder;
import org.jetbrains.javafx.testUtils.JavaFxLightFixtureTestCase;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public class JavaFxSurroundWithTest extends JavaFxLightFixtureTestCase {
  public void testParentheses() {
    doTest(new JavaFxWithParenthesesSurrounder());
  }

  public void testNot() {
    doTest(new JavaFxWithNotSurrounder());
  }

  public void testAs() {
    doTest(new JavaFxWithAsSurrounder());
  }

  public void testNotInstanceof() {
    doTest(new JavaFxWithNotInstanceofSurrounder());
  }

  private void doTest(final Surrounder surrounder) {
    final String baseName = "/surround/" + getTestName(false);
    myFixture.configureByFile(baseName + ".fx");
    new WriteCommandAction.Simple(myFixture.getProject()) {
      @Override
      protected void run() throws Throwable {
        SurroundWithHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder);
      }
    }.execute();
    myFixture.checkResultByFile(baseName + "_after.fx", true);
  }
}
