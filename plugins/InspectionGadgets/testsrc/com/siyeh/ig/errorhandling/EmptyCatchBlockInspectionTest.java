package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

public class EmptyCatchBlockInspectionTest extends LightInspectionTestCase {

  @Override
  protected LocalInspectionTool getInspection() {
    final EmptyCatchBlockInspection tool = new EmptyCatchBlockInspection();
    tool.m_includeComments = true;
    tool.m_ignoreTestCases = true;
    tool.m_ignoreIgnoreParameter = true;
    return tool;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package junit.framework; public abstract class TestCase {}"
    };
  }

  public void testSimple() {
    doTest("class C {\n" +
           "  void m() {\n" +
           "    try {\n" +
           "      throw new Exception();\n" +
           "    } /*Empty 'catch' block*/catch/**/ (Exception e) {\n" +
           "    }\n" +
           "  }\n" +
           "}\n");
  }

  public void testEmptyStatement() {
    doTest("class C {\n" +
           "  void m() {\n" +
           "    try {\n" +
           "      throw new Exception();\n" +
           "    } /*Empty 'catch' block*/catch/**/ (Exception e) {\n" +
           "      ;\n" +
           "    }\n" +
           "  }\n" +
           "}\n");
  }

  public void testComment() {
    doTest("class C {\n" +
           "  void m() {\n" +
           "    try {\n" +
           "      throw new Exception();\n" +
           "    } catch (Exception e) {\n" +
           "      // comment\n" +
           "    }\n" +
           "  }\n" +
           "}\n");
  }

  public void testIgnored() {
    doTest("class C {\n" +
           "  void m() {\n" +
           "    try {\n" +
           "      throw new Exception();\n" +
           "    } catch (Exception ignored) {\n" +
           "    }\n" +
           "  }\n" +
           "}\n");
  }
}
