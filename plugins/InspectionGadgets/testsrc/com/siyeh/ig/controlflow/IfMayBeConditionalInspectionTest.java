package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class IfMayBeConditionalInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/controlflow/if_may_be_conditional", new IfMayBeConditionalInspection());
    }
}