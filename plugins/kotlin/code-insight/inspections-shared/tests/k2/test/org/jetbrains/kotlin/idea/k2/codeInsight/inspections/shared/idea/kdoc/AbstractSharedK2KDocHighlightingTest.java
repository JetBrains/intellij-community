// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.idea.kdoc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingTest;
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor;
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor;

public abstract class AbstractSharedK2KDocHighlightingTest extends AbstractHighlightingTest {
    @NotNull
    @Override
    protected KotlinLightProjectDescriptor getDefaultProjectDescriptor() {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance();
    }
}
