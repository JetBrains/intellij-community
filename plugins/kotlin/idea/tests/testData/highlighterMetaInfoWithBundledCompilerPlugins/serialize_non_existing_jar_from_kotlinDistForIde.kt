// COMPILER_ARGUMENTS: -Xplugin=$TEST_KOTLIN_DIST_FOR_IDE$/fake/location/kotlinx-serialization-compiler-plugin.jar
// FILE: main.kt
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package test

import kotlinx.serialization.Serializable

@Serializable
class BaseClass

val companionRef: BaseClass.Companion = BaseClass

fun test() {
    BaseClass.serializer()
}

