package com.siyeh.ig.logging;

import com.siyeh.ig.IGInspectionTestCase;

public class LogStatementGuardedByLogConditionInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final LogStatementGuardedByLogConditionInspection tool = new LogStatementGuardedByLogConditionInspection();
    tool.loggerClassName = "com.siyeh.igtest.logging.log_statement_guarded_by_log_condition.LogStatementGuardedByLogCondition.Logger";
    tool.logMethodNameList.clear();
    tool.logMethodNameList.add("debug");
    tool.logConditionMethodNameList.clear();
    tool.logConditionMethodNameList.add("isDebug");
    doTest("com/siyeh/igtest/logging/log_statement_guarded_by_log_condition", tool);
  }
}