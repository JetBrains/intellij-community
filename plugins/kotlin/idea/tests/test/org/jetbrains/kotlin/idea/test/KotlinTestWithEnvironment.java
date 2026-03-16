// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;

public abstract class KotlinTestWithEnvironment extends KotlinTestWithEnvironmentManagement
        implements ExpectedPluginModeProvider {

    private KotlinCoreEnvironment environment;

    @Override
    protected void setUp() throws Exception {
        ExpectedPluginModeProviderKt.setUpWithKotlinPlugin(this,
                                                           getTestRootDisposable(),
                                                           super::setUp);
        environment = createEnvironment();
    }

    @Override
    protected void tearDown() throws Exception {
        environment = null;
        WriteAction.runAndWait(() -> super.tearDown());
    }

    protected abstract KotlinCoreEnvironment createEnvironment() throws Exception;

    @NotNull
    public KotlinCoreEnvironment getEnvironment() {
        return environment;
    }

    @NotNull
    public Project getProject() {
        return getEnvironment().getProject();
    }
}
