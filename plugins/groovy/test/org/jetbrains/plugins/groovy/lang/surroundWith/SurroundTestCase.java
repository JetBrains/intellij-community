package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;

import java.util.List;

/**
 * @author peter
 */
public abstract class SurroundTestCase extends LightGroovyTestCase {
  protected void doTest(Surrounder surrounder) throws Exception {
    final List<String> data = SimpleGroovyFileSetTestCase.readInput(getTestDataPath() + "/" + getTestName(true) + ".test");
    final String fileText = data.get(0);
    myFixture.configureByText("a.groovy", fileText);

    SurroundWithHandler.invoke(getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder);
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    PsiUtil.reformatCode(myFixture.getFile());
    assertEquals(data.get(1), myFixture.getFile().getText().trim());
  }
}
