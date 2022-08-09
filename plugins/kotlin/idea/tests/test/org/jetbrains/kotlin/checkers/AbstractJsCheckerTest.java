// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.checkers;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase;
import org.jetbrains.kotlin.idea.test.KotlinStdJSProjectDescriptor;

public abstract class AbstractJsCheckerTest extends KotlinLightCodeInsightFixtureTestCase {
    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return KotlinStdJSProjectDescriptor.INSTANCE;
    }

    public void doTest(String unused) {
        myFixture.configureByFile(fileName());
        myFixture.checkHighlighting(true, false, false);
    }
}
