// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR/serialize_fake_plugin.jar
// FILE: main.kt
package test

import kotlinx.serialization.Serializable

@Serializable
class BaseClass

val companionRef: BaseClass.Companion = BaseClass

fun test() {
    BaseClass.serializer()
}

