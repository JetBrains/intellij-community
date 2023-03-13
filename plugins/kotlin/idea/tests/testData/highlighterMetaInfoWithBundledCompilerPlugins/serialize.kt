// COMPILER_ARGUMENTS: -Xplugin=non_existent_location/kotlin-serialization-dev.jar
// FILE: main.kt
package test

import kotlinx.serialization.Serializable

@Serializable
class BaseClass

val companionRef: BaseClass.Companion = BaseClass

fun test() {
    BaseClass.serializer()
}

