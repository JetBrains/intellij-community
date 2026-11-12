// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The helpers of the tests of {@link CodeBlockGenerator}.
 */
public final class CodeBlockGenerationTestUtil {
  /**
   * The test data path of the code block generation tests.
   */
  public static final String TEST_DATA_PATH = TestUtils.getAbsoluteTestDataPath() + "refactoring/convertGroovyToJava/codeBlock";

  private CodeBlockGenerationTestUtil() {
  }

  /**
   * Converts the statements of {@code <test name>.groovy} to Java, and compares the result with {@code <test name>.java}.
   */
  public static void doTest(LightJavaCodeInsightTestFixture fixture) {
    final String testName = fixture.getTestName(true);
    final PsiFile file = fixture.configureByFile(testName + ".groovy");
    assertInstanceOf(GroovyFile.class, file);
    final PsiFile result = ReadAction.computeBlocking(() -> {
      GrTopStatement[] statements = ((GroovyFile)file).getTopStatements();
      final StringBuilder builder = new StringBuilder();
      CodeBlockGenerator generator = new CodeBlockGenerator(builder, new ExpressionContext(fixture.getProject(), GroovyFile.EMPTY_ARRAY));
      for (GrTopStatement statement : statements) {
        statement.accept(generator);
        builder.append("\n");
      }
      return PsiFileFactory.getInstance(fixture.getProject())
        .createFileFromText(testName + ".java", JavaLanguage.INSTANCE, builder.toString(), false, true);
    });
    EdtTestUtil.runInEdtAndWait(() -> PostprocessReformattingAspect.getInstance(fixture.getProject()).doPostponedFormatting());
    final String text = ReadAction.computeBlocking(() -> result.getText());
    PlatformTestUtil.assertSameLinesWithFile(Path.of(fixture.getTestDataPath(), testName + ".java").toString(), text);
  }

  /**
   * Adds the Groovy file {@code Bar.groovy} with {@code text} to the project.
   */
  public static PsiFile addFile(LightJavaCodeInsightTestFixture fixture, String text) {
    return fixture.addFileToProject("Bar.groovy", text);
  }
}
