package com.siyeh.ig.logging;

import com.siyeh.ig.IGInspectionTestCase;

public class LogStatementGuardedByLogConditionInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/logging/log_statement_guarded_by_log_condition",
                new LogStatementGuardedByLogConditionInspection());
    }
}