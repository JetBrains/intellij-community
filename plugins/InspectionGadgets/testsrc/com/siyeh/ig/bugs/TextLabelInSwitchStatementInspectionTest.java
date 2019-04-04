// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class TextLabelInSwitchStatementInspectionTest extends LightInspectionTestCase {

  public void testSwitchStatement() {
    doTest("class Type {\n" +
           "    void x(E e) {\n" +
           "        switch (e) {\n" +
           "            case A, B:\n" +
           "                /*Text label 'caseZ:' in 'switch' statement*/caseZ/**/: break;\n" +
           "            case C:\n" +
           "                break;\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  public void testSwitchExpression() {
    doTest("class Type {\n" +
           "    void x(E e) {\n" +
           "        int i = switch (e) {\n" +
           "            case A,B:\n" +
           "            /*Text label 'caseZ:' in 'switch' expression*/caseZ/**/: break 1;\n" +
           "            case C: break 0;\n" +
           "        };\n" +
           "    }\n" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new TextLabelInSwitchStatementInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_12;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "enum E {\n" +
      "    A,B,C\n" +
      "}"
    };
  }
}