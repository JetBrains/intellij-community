package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class EmptyStatementBodyInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final EmptyStatementBodyInspection tool = new EmptyStatementBodyInspection();
    tool.m_reportEmptyBlocks = true;
    tool.commentsAreContent = true;
    doTest("com/siyeh/igtest/bugs/empty_statement_body", tool);
  }
}