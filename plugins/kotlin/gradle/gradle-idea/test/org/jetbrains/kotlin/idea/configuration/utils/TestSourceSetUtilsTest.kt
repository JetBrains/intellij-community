package org.jetbrains.kotlin.idea.configuration.utils

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