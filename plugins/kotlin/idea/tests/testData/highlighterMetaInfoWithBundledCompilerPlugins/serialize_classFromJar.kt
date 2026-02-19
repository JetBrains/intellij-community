// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/serialize_fake_plugin.jar
// FILE: main.kt
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package test

// JsonElement is already compiled
import kotlinx.serialization.json.JsonElement

val companionRefShort: JsonElement.Companion = JsonElement
val companionRefFull: JsonElement.Companion = JsonElement.Companion

fun test() {
    JsonElement.serializer()
    JsonElement.Companion.serializer()
}

