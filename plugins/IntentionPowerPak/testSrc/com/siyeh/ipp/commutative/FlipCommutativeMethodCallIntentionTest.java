// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.commutative;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

public class FlipCommutativeMethodCallIntentionTest extends IPPTestCase {
    public void testSubstitution() { doTest(); }
    public void testNoQualifier() { doTest(); }
    public void testThisQualifier() { doTest(); }
    public void testNoQualifierStatic() { assertIntentionNotAvailable(); }
    public void testNoQualifier2() { assertIntentionNotAvailable(); }
    public void testSameQualifierArgument() { assertIntentionNotAvailable(); }
    public void testArgumentCastNoQualifier() { doTest(); }

    @Override
    protected String getIntentionName() {
        return IntentionPowerPackBundle.message("flip.commutative.method.call.intention.name1", "foo");
    }

    @Override
    protected String getRelativePath() {
        return "commutative/flip";
    }
}
