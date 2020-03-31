/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.concatenation;

import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

public class JoinConcatenatedStringLiteralsIntentionTest extends IPPTestCase {
    public void testSimple() { doTest(); }
    public void testPolyadic() { doTest(); }
    public void testNonString() { doTest(); }
    public void testNonString2() { doTest(); }
    public void testNotAvailable() { assertIntentionNotAvailable(); }
    public void testInvalidLiteral() { assertIntentionNotAvailable(); }
    public void testKeepCommentsAndWhitespace() { doTest(); }
    public void testTextBlocks() { doTest(); }
    public void testTextBlocksTailingLineBreak() { doTest(); }
    public void testTextBlocksAndStringLiteral() { doTest(); }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JAVA_13;
    }

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
