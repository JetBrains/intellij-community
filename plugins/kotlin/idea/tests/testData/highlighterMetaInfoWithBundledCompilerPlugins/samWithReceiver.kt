// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/samWithReceiver_fake_plugin.jar -P plugin:org.jetbrains.kotlin.samWithReceiver:annotation=test.MySamMarker
// FILE: main.kt
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
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