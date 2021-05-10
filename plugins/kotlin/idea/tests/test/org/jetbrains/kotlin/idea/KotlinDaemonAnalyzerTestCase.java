// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Ref;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;

import static com.intellij.testFramework.RunAll.runAll;

abstract public class KotlinDaemonAnalyzerTestCase extends DaemonAnalyzerTestCase {
    private Ref<Disposable> vfsDisposable;
    @Override
    protected void setUp() throws Exception {
        vfsDisposable = KotlinTestUtils.allowProjectRootAccess(this);
        super.setUp();
    }

    @Override
    protected void tearDown() {
        runAll(
                () -> super.tearDown(),
                () -> KotlinTestUtils.disposeVfsRootAccess(vfsDisposable)
        );
    }
}
