// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/dataframe_fake_plugin.jar
// FILE: main.kt
// CHECK_SYMBOL_NAMES
// ALLOW_ERRORS
// HIGHLIGHTER_ATTRIBUTES_KEY
package test

import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf

fun testCast() {
    val df = dataFrameOf("firstName")(
        "Alice"
    ).cast<Person>()
// } // commented on purpose to make Person class "accidentally" local 
    
@DataSchema
interface Person {
    val firstName: String
}
