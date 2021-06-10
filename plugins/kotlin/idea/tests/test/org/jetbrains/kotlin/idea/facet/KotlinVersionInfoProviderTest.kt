// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.facet

import org.jetbrains.kotlin.config.LanguageVersion
import org.junit.Test
import kotlin.test.assertSame

class KotlinVersionInfoProviderTest {
    @Test
    fun supportedLanguageVersionConsistency() {
        LanguageVersion.values().forEach { languageVersion ->
            assertSame(languageVersion, languageVersion.toString().toLanguageVersion())
        }
    }
}