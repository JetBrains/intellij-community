// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.navigation;

import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils;
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase;

import static org.jetbrains.kotlin.idea.navigation.GotoCheck.checkGotoDirectives;

public abstract class AbstractKotlinGotoTest extends KotlinLightCodeInsightFixtureTestCase {
    protected void doSymbolTest(String path) {
        myFixture.configureByFile(path);
        String directive = getIgnoreDirective();
        if (directive != null && InTextDirectivesUtils.isDirectiveDefined(myFixture.getEditor().getDocument().getText(), directive)) {
            return;
        }
        checkGotoDirectives(new GotoSymbolModel2(getProject(), myFixture.getTestRootDisposable()), myFixture.getEditor());
    }

    protected String getIgnoreDirective() {
        return null;
    }

    protected void doClassTest(String path) {
        myFixture.configureByFile(path);
        checkGotoDirectives(new GotoClassModel2(getProject()), myFixture.getEditor());
    }
}