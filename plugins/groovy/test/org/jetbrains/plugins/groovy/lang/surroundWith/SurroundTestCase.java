package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author peter
 */
public abstract class SurroundTestCase extends LightGroovyTestCase {
  protected void doTest(final Surrounder surrounder) throws Exception {
    final List<String> data = TestUtils.readInput(getTestDataPath() + "/" + getTestName(true) + ".test");
    final String fileText = data.get(0);
    myFixture.configureByText("a.groovy", fileText);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        SurroundWithHandler.invoke(getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder);
        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      }
    });

    assertEquals(data.get(1), myFixture.getFile().getText().trim());
  }
}
