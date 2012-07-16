package com.siyeh.ipp.concatenation;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

public class JoinConcatenatedStringLiteralsIntentionTest extends IPPTestCase {
    public void testSimple() { doTest(); }
    public void testPolyadic() { doTest(); }
    public void testNonString() { doTest(); }
    public void testNonString2() { doTest(); }
    public void testNotAvailable() { assertIntentionNotAvailable(); }
    public void testKeepCommentsAndWhitespace() { doTest(); }

    @Override
    protected String getIntentionName() {
        return IntentionPowerPackBundle.message(
                "join.concatenated.string.literals.intention.name");
    }

    @Override
    protected String getRelativePath() {
        return "concatenation/join_concat";
    }
}
