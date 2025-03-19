// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/serialize_fake_plugin.jar
// FILE: main.kt
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package test

import kotlinx.serialization.Serializable

// import from generated class, see KT-59732
import test.BaseClass.Companion.serializer

@Serializable
class BaseClass

fun test() {
    serializer()
}

