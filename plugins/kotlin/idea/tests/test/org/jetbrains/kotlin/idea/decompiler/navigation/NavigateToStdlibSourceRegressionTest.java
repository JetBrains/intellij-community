// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.navigation;

import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources;
import org.jetbrains.kotlin.idea.test.TestUtilsKt;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.RunWith;

import static com.intellij.testFramework.RunAll.runAll;

@RunWith(JUnit38ClassRunner.class)
public class NavigateToStdlibSourceRegressionTest extends NavigateToLibraryRegressionTest {
    /**
     * Regression test against KT-3186
     */
    public void testRefToAssertEquals() {
        PsiElement navigationElement = configureAndResolve("import kotlin.io.createTempDir; val x = <caret>createTempDir()");
        assertEquals("Utils.kt", navigationElement.getContainingFile().getName());
    }

    @Override
    protected void tearDown() {
        runAll(
                () -> super.tearDown(),
                () -> {
                    // Workaround for IDEA's bug during tests.
                    // After tests IDEA disposes VirtualFiles within LocalFileSystem, but doesn't rebuild indices.
                    // This causes library source files to be impossible to find via indices
                    TestUtilsKt.closeAndDeleteProject();
                }
        );
    }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources();
    }
}
