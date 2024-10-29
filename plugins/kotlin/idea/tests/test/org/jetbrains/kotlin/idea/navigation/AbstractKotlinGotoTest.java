// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.navigation;

import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils;
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.jetbrains.kotlin.idea.navigation.GotoCheck.checkGotoResult;

public abstract class AbstractKotlinGotoTest extends KotlinLightCodeInsightFixtureTestCase {
    protected void doSymbolTest(String path) {
        myFixture.configureByFile(path);
        String directive = getIgnoreDirective();
        if (directive != null && InTextDirectivesUtils.isDirectiveDefined(myFixture.getEditor().getDocument().getText(), directive)) {
            return;
        }
        checkGotoResult(
                new GotoSymbolModel2(getProject(), myFixture.getTestRootDisposable()), myFixture.getEditor(),
                getExpectedFile(Paths.get(path))
        );
    }

    private static @NotNull Path getExpectedFile(Path nioPath) {
        return nioPath.getParent().resolve(nioPath.getFileName().toString().replace(".kt", ".result.txt"));
    }

    protected String getIgnoreDirective() {
        return null;
    }

    protected void doClassTest(String path) {
        myFixture.configureByFile(path);
        checkGotoResult(new GotoClassModel2(getProject()), myFixture.getEditor(), getExpectedFile(Paths.get(path)));
    }
}