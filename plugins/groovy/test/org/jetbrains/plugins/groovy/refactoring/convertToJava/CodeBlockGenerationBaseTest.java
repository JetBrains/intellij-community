package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.nio.file.Path;

public abstract class CodeBlockGenerationBaseTest extends LightGroovyTestCase {
  protected void doTest() {
    final String testName = getTestName(true);
    final PsiFile file = myFixture.configureByFile(testName + ".groovy");
    UsefulTestCase.assertInstanceOf(file, GroovyFile.class);

    GrTopStatement[] statements = ((GroovyFile)file).getTopStatements();
    final StringBuilder builder = new StringBuilder();
    CodeBlockGenerator generator = new CodeBlockGenerator(builder, new ExpressionContext(getProject(), GroovyFile.EMPTY_ARRAY));
    for (GrTopStatement statement : statements) {
      statement.accept(generator);
      builder.append("\n");
    }

    final PsiFile result = createLightFile(testName + ".java", JavaLanguage.INSTANCE, builder.toString());
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    final String text = result.getText();
    UsefulTestCase.assertSameLinesWithFile(Path.of(getTestDataPath(), testName + ".java").toString(), text);
  }

  protected PsiFile addFile(String text) {
    return myFixture.addFileToProject("Bar.groovy", text);
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = TestUtils.getTestDataPath() + "refactoring/convertGroovyToJava/codeBlock";
}
