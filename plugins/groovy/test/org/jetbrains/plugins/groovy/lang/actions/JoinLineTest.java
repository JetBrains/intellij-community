// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.actions;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class JoinLineTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testVariable() {
    doTest("""
             d<caret>ef a;
             a = 4
             """, """
             def a = 4;
             """);
  }

  @Test
  public void testVar2() {
    doTest("""
             d<caret>ef a, b;
             a = 4
             """, """
             def a = 4, b;
             """);
  }

  @Test
  public void testVar3() {
    doTest("""
             d<caret>ef a
             a = 4
             """, """
             def a = 4
             """);
  }

  @Test
  public void testVar4() {
    doTest("""
             d<caret>ef a, b
             a = 4
             """, """
             def a = 4, b
             """);
  }

  @Test
  public void testIf() {
    doTest("""
             if (cond)<caret> {
               doSmth()
             }
             """, """
             if (cond) doSmth()
             """);
  }

  @Test
  public void testElse() {
    doTest("""
             if (cond) {
               doSmth()
             }
             els<caret>e {
               doSmthElse()
             }
             """, """
             if (cond) {
               doSmth()
             }
             else doSmthElse()
             """);
  }

  @Test
  public void testJoinStatements1() {
    doTest("""
             prin<caret>t 2
             print 2
             """, """
             print 2; <caret>print 2
             """);
  }

  @Test
  public void testJoinStatements2() {
    doTest("""
             print 2;
             print 2
             """, """
             print 2; <caret>print 2
             """);
  }

  @Test
  public void testJoinStatements3() {
    doTest("""
             prin<caret>t 2
             
             print 2
             """, """
             print 2<caret>
             print 2
             """);
  }

  @Test
  public void testJoinStatements4() {
    doTest("""
             <selection>print 2
             
             print 2</selection>
             """, """
             <selection>print 2; print 2</selection>
             """);
  }

  @Test
  public void testFor() {
    doTest("""
             for (;a<caret>;) {
               print 2
             }\s""", """
             for (;a;) <caret>print 2""");
  }

  @Test
  public void testIfWithForceBraces() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CommonCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(GroovyLanguage.INSTANCE);
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    doTest("""
             if (a)
               print 2
             """, """
             if (a) <caret>print 2
             """);
  }

  private void doTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before);
    GroovyEditorActionTestUtil.performAction(myFixture, IdeActions.ACTION_EDITOR_JOIN_LINES);
    myFixture.checkResult(after);
  }
}
