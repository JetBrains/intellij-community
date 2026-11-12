// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrReturnPointHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testReturnPoint1() {
    doTest("""
             private static getWorldType(stepFile) {
                 final worldType //unused
                 if (som) throw new IOException()

                 for (statement in stepFile.statements) {
                     if (statement instanceof String && isWorldDeclaration(statement)) {
                         final closure = getClosureArg(statement)
                         re<caret>turn closure.returnType
                     }
                 }
             }
             """, "throw new IOException()", "return closure.returnType");
  }

  @Test
  public void testReturnPoint2() {
    doTest("""
             private static getWorldType(stepFile) {
                 final worldType //unused
                 if (som) throw new IOException()

                 for (statement in stepFile.statements) {
                     if (statement instanceof String && isWorldDeclaration(statement)) {
                         final closure = getClosureArg(statement)
                         re<caret>turn closure.returnType
                     }
                 }
                 2
             }
             """, "throw new IOException()", "return closure.returnType", "2");
  }

  private void doTest(final String text, final String... usages) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", text);
    EdtTestUtil.runInEdtAndWait(() -> {
      HighlightUsagesHandlerBase<PsiElement> handler = HighlightUsagesHandler.createCustomHandler(myFixture.getEditor(), myFixture.getFile());
      assertNotNull(handler);
      List<PsiElement> targets = handler.getTargets();
      assertEquals(1, targets.size());
      assertEquals("return", targets.get(0).getText());

      handler.computeUsages(targets);
      List<TextRange> readUsages = handler.getReadUsages();
      assertEquals(usages.length, readUsages.size());

      String fileText = myFixture.getFile().getText();
      List<String> textUsages = ContainerUtil.map(readUsages, r -> r.substring(fileText));
      assertThat(Arrays.asList(usages)).hasSameSizeAs(textUsages).hasSameElementsAs(textUsages);
    });
  }
}
