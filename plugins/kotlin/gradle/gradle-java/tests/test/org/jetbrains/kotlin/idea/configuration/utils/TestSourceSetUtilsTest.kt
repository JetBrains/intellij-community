// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.configuration.utils

import org.jetbrains.kotlin.idea.gradle.configuration.utils.*
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(UnsafeTestSourceSetHeuristicApi::class)
class TestSourceSetUtilsTest {

    @Test
    fun `test predicted production source set name for various examples`() {
        assertEquals("commonMain", predictedProductionSourceSetName("commonTest"))
        assertEquals("main", predictedProductionSourceSetName("test"))
        assertEquals("abcMain", predictedProductionSourceSetName("abc"))
        assertEquals("abctestMain", predictedProductionSourceSetName("abctest"))
    }
}