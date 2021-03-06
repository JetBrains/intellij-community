/*
 * Copyright 2011 Bas Leijdekkers
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

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

public class ReplaceConcatenationWithFormatStringTest extends IPPTestCase {
    public void testNumericBinaryExpression() { doTest(); }
    public void testHexadecimalLiteral() { doTest(); }
    public void testPercentInLiteral() { doTest(); }
    public void testParameters() { doTest(); }
    public void testLineSeparator() { doTest(); }
    public void testPreserveTextBlock() {
        IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15, () -> {
            doTest(IntentionPowerPackBundle.message(
              "replace.concatenation.with.format.string.intention.name.formatted"));
        });
    }

    @Override
    protected String getIntentionName() {
        return IntentionPowerPackBundle.message(
                "replace.concatenation.with.format.string.intention.name");
    }

    @Override
    protected String getRelativePath() {
        return "concatenation/string_format";
    }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JAVA_11;
    }
}
