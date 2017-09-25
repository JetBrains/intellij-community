package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class SwitchStatementWithConfusingDeclarationInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/controlflow/switch_statement_with_confusing_declaration",
           new SwitchStatementWithConfusingDeclarationInspection());
  }
}