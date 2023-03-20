// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.kdoc;

import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.KDocUnresolvedReferenceInspection;
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingTest;
import org.jetbrains.kotlin.idea.inspections.kdoc.KDocMissingDocumentationInspection;

public abstract class AbstractKDocHighlightingTest extends AbstractHighlightingTest {
    @Override
    protected void setUp() {
        super.setUp();
        myFixture.enableInspections(KDocMissingDocumentationInspection.class);
    }
}
