// COMPILER_ARGUMENTS: -Xplugin=$KOTLIN_BUNDLED$/lib/kotlin-dataframe-compiler-plugin-experimental.jar
// FILE: main.kts
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY

import org.jetbrains.kotlinx.dataframe.*
import org.jetbrains.kotlinx.dataframe.api.*

fun local(df: DataFrame<*>) {
    val df1 = df.add("column") { 41.inc() + "1".toInt() }
    df1.column
}