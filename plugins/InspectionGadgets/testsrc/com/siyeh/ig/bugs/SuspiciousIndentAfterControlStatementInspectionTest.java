package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class SuspiciousIndentAfterControlStatementInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/suspicious_indent_after_control_statement",
           new SuspiciousIndentAfterControlStatementInspection());
  }
}