package com.siyeh.ig.logging;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class LogStatementGuardedByLogConditionInspectionTest extends LightInspectionTestCase {

  public void testLogStatementGuardedByLogCondition() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final LogStatementGuardedByLogConditionInspection inspection = new LogStatementGuardedByLogConditionInspection();
    inspection.loggerClassName = "com.siyeh.igtest.logging.log_statement_guarded_by_log_condition.LogStatementGuardedByLogCondition.Logger";
    inspection.logMethodNameList.clear();
    inspection.logMethodNameList.add("debug");
    inspection.logConditionMethodNameList.clear();
    inspection.logConditionMethodNameList.add("isDebug");
    return inspection;
  }
}