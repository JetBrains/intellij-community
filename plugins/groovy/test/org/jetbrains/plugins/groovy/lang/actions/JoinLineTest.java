// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.actions;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;

public class JoinLineTest extends GroovyEditorActionTestBase {
  public void testVariable() {
    doTest("""
             d<caret>ef a;
             a = 4
             """, """
             def a = 4;
             """);
  }

  public void testVar2() {
    doTest("""
             d<caret>ef a, b;
             a = 4
             """, """
             def a = 4, b;
             """);
  }

  public void testVar3() {
    doTest("""
             d<caret>ef a
             a = 4
             """, """
             def a = 4
             """);
  }

  public void testVar4() {
    doTest("""
             d<caret>ef a, b
             a = 4
             """, """
             def a = 4, b
             """);
  }

  public void testIf() {
    doTest("""
             if (cond)<caret> {
               doSmth()
             }
             """, """
             if (cond) doSmth()
             """);
  }

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

  public void testJoinStatements1() {
    doTest("""
             prin<caret>t 2
             print 2
             """, """
             print 2; <caret>print 2
             """);
  }

  public void testJoinStatements2() {
    doTest("""
             print 2;
             print 2
             """, """
             print 2; <caret>print 2
             """);
  }

  public void testJoinStatements3() {
    doTest("""
             prin<caret>t 2
             
             print 2
             """, """
             print 2<caret>
             print 2
             """);
  }

  public void testJoinStatements4() {
    doTest("""
             <selection>print 2
             
             print 2</selection>
             """, """
             <selection>print 2; print 2</selection>
             """);
  }

  public void testFor() {
    doTest("""
             for (;a<caret>;) {
               print 2
             }\s""", """
             for (;a;) <caret>print 2""");
  }

  public void testIfWithForceBraces() {
    CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(GroovyLanguage.INSTANCE);
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    doTest("""
             if (a)
               print 2
             """, """
             if (a) <caret>print 2
             """);
  }

  private void doTest(String before, String after) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before);
    performAction(IdeActions.ACTION_EDITOR_JOIN_LINES);
    myFixture.checkResult(after);
  }
}
