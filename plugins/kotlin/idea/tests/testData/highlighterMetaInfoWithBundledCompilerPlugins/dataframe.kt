// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/dataframe_fake_plugin.jar
// FILE: main.kt
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package test

import org.jetbrains.kotlinx.dataframe.*
import org.jetbrains.kotlinx.dataframe.api.*

fun main() {
    val df = dataFrameOf("a" to listOf("1"), "b" to listOf(52))
    df.a

    val df1 = df.add("newCol") { a + b.toString() }
    df1.newCol
}