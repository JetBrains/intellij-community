package org.jetbrains.javafx;

import com.intellij.codeInsight.intention.IntentionAction;
import org.jetbrains.javafx.testUtils.JavaFxLightFixtureTestCase;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public class JavaFxIntentionTest extends JavaFxLightFixtureTestCase {
  private void doTest(final String hint) {
    final String baseName = "/intentions/" + getTestName(false);
    myFixture.configureByFile(baseName + ".fx");
    final IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.launchAction(action);
    myFixture.checkResultByFile(baseName + "_after.fx");
  }

  public void testRemovePrivate() {
    doTest(JavaFxBundle.message("INTN.remove.private.keyword"));
  }
}
