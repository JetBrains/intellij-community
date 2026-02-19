// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter;

import org.jetbrains.kotlin.idea.base.fe10.highlighting.suspender.KotlinHighlightingSuspender;
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager;

public abstract class AbstractK1HighlightingTest extends AbstractHighlightingTest {
    @Override
    protected void updateScriptDependencies() {
        ScriptConfigurationManager.Companion.updateScriptDependenciesSynchronously(myFixture.getFile());
    }

    @Override
    protected void initializeHighlightingSuspender() {
        KotlinHighlightingSuspender.Companion.getInstance(myFixture.getProject());
    }
}
