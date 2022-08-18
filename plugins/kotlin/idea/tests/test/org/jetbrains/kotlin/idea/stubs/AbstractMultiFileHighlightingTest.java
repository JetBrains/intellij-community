// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubs;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.test.AstAccessControl;

import java.io.File;

import static org.jetbrains.kotlin.idea.test.TestUtilsKt.IDEA_TEST_DATA_DIR;

// This test is quite old and is partially failing after IDEA 2018.2
// ALLOW_AST_ACCESS is added to 'util.kt' in test data to mute the failure
// Possible solutions:
// 1. Review and expand test data and fix platform issues leading to test failures
// 2. Remove the test completely if it's considered to have no value anymore
public abstract class AbstractMultiFileHighlightingTest extends AbstractMultiHighlightingTest {

    public void doTest(@NotNull String filePath) throws Exception {
        configureByFile(new File(filePath).getName(), "");
        boolean shouldFail = getName().contains("UnspecifiedType");
        AstAccessControl.INSTANCE.testWithControlledAccessToAst(
                shouldFail, getFile().getVirtualFile(), getProject(), getTestRootDisposable(),
                new Function0<Unit>() {
                    @Override
                    public Unit invoke() {
                        checkHighlighting(myEditor, true, false);
                        return Unit.INSTANCE;
                    }
                }
        );
    }

    @NotNull
    @Override
    public File getTestDataDirectory() {
        return new File(IDEA_TEST_DATA_DIR, "multiFileHighlighting");
    }

    @Override
    protected Sdk getTestProjectJdk() {
        return IdeaTestUtil.getMockJdk18();
    }
}
