// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature

/**
 * Implement this interface instead of [TestFeature] for correct interaction with file highlighting.
 * Test features that use additional markup should restore the markup for file comparison.
 *
 * Note on not using file sanitation in the highlighting check.
 * It's possible to remove all additional markup from test data files, but it's not done this way on purpose.
 * The sanitized file comparison error result can't be used to update test data files in case of expected changes.
 * Adding markup manually is time-consuming and error-prone.
 *
 * It's currently forbidden to have more than one [TestFeatureWithFileMarkup] enabled alongside highlighting.
 * Otherwise, markup restoration from multiple independent features can lead to unexpected results.
 * Therefore, all implementations of [TestFeatureWithFileMarkup] should be non-mandatory and disabled by default.
 */
interface TestFeatureWithFileMarkup<T : Any> : TestFeature<T> {
    override fun isEnabledByDefault(): Boolean = false

    /**
     * Update the file text and return the markup removed during the project setup
     *
     * @param text text after highlighting check before comparison against the expected file
     * @param editor [Editor] containing the file
     * @return text with updated markup or `null` in case of no changes
     */
    fun KotlinMppTestsContext.restoreMarkup(text: String, editor: Editor): String?
}
