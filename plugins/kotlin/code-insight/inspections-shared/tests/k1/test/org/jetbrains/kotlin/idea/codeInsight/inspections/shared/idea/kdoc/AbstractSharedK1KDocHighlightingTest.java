// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.idea.kdoc;

import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.KDocUnresolvedReferenceInspection;
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingTest;

public abstract class AbstractSharedK1KDocHighlightingTest extends AbstractHighlightingTest {
    @Override
    protected void setUp() {
        super.setUp();
        myFixture.enableInspections(KDocUnresolvedReferenceInspection.class);
    }
}
