// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.HeadlessRenameProcessor;
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor;
import com.intellij.refactoring.rename.HeadlessRenameResult;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.refactoring.rename.GrInlineTransformationElementRenamer;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GinqHeadlessRenameTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GinqTestUtils.getProjectDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testAliasOfADataSourceIsRenamed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("query.groovy", """
      GQ {
        from n in [1, 2, 3]
        select n
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiElement alias = file.findElementAt(file.getText().indexOf("n in")).getParent();
      assertInstanceOf(GrInlineTransformationElementRenamer.class, HeadlessRenamePsiElementProcessor.processorOf(alias));

      HeadlessRenameResult planned = HeadlessRenameProcessor.analyze(myFixture.getProject(), alias, "m");
      HeadlessRenameResult result = assertInstanceOf(HeadlessRenameResult.Planned.class, planned).getPlan().apply();

      assertInstanceOf(HeadlessRenameResult.Applied.class, result);
      assertEquals("""
                     GQ {
                       from m in [1, 2, 3]
                       select m
                     }
                     """, file.getText());
    });
  }

  /** The macro GQ has another name, so it overrides no usage of the renamed field. */
  @Test
  public void testMacroIsNoCollisionOfAFieldRename() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Counter.groovy", """
      class Counter {
        int hits
      }
      """);
    PsiFile reader = myFixture.addFileToProject("Reader.groovy", """
      class Reader {
        def read(Counter counter) {
          counter.getHits()
        }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiField hits = myFixture.findClass("Counter").findFieldByName("hits", false);
      assertNotNull(hits, "no field hits in the fixture");

      HeadlessRenameResult planned = HeadlessRenameProcessor.analyze(myFixture.getProject(), hits, "count");
      HeadlessRenameResult result = assertInstanceOf(HeadlessRenameResult.Planned.class, planned).getPlan().apply();

      assertInstanceOf(HeadlessRenameResult.Applied.class, result);
      assertTrue(reader.getText().contains("counter.getCount()"), "the usage kept the old name: " + reader.getText());
    });
  }
}
