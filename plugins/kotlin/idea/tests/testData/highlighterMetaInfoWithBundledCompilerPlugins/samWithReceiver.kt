// COMPILER_ARGUMENTS: -Xplugin=non_existent_location/kotlin-sam-with-receiver-dev.jar -P plugin:org.jetbrains.kotlin.samWithReceiver:annotation=test.MySamMarker
// FILE: main.kt
package test

import JavaSamInterface

annotation class MySamMarker

fun test() {
    JavaSamInterface {
        val receiver: String = this
    }
}

// FILE: JavaSamInterface.java
import test.MySamMarker;

@MySamMarker
public interface JavaSamInterface {
    void doStuff(String param);
}