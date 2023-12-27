// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/lombok_fake_plugin.jar
// FILE: main.kt
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package test

import JavaValueClass

fun test(value: JavaValueClass) {
    val saved: String = value.foo
    value.bar = saved
}

// FILE: JavaValueClass.java
import lombok.Getter;
import lombok.Setter;

public class JavaValueClass {
    @Getter
    private final String foo;

    @Getter
    @Setter
    private String bar;
}