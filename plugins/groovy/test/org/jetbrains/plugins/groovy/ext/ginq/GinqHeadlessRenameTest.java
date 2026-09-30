// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.HeadlessRenameProcessor;
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor;
import com.intellij.refactoring.rename.HeadlessRenameResult;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.refactoring.rename.GrInlineTransformationElementRenamer;

public class GinqHeadlessRenameTest extends LightGroovyTestCase {
  @Override
  public @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GinqTestUtils.getProjectDescriptor();
  }

  public void testAliasOfADataSourceIsRenamed() {
    PsiFile file = myFixture.addFileToProject("query.groovy", """
      GQ {
        from n in [1, 2, 3]
        select n
      }
      """);
    PsiElement alias = file.findElementAt(file.getText().indexOf("n in")).getParent();
    assertInstanceOf(HeadlessRenamePsiElementProcessor.processorOf(alias), GrInlineTransformationElementRenamer.class);

    HeadlessRenameResult planned = HeadlessRenameProcessor.analyze(getProject(), alias, "m");
    HeadlessRenameResult result = assertInstanceOf(planned, HeadlessRenameResult.Planned.class).getPlan().apply();

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    assertEquals("""
                   GQ {
                     from m in [1, 2, 3]
                     select m
                   }
                   """, file.getText());
  }

  /** The macro GQ has another name, so it overrides no usage of the renamed field. */
  public void testMacroIsNoCollisionOfAFieldRename() {
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
    PsiField hits = myFixture.findClass("Counter").findFieldByName("hits", false);
    assertNotNull("no field hits in the fixture", hits);

    HeadlessRenameResult planned = HeadlessRenameProcessor.analyze(getProject(), hits, "count");
    HeadlessRenameResult result = assertInstanceOf(planned, HeadlessRenameResult.Planned.class).getPlan().apply();

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    assertTrue("the usage kept the old name: " + reader.getText(), reader.getText().contains("counter.getCount()"));
  }
}
