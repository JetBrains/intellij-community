// COMPILER_ARGUMENTS: -Xplugin=non_existent_location/kotlin-lombok-dev.jar
// FILE: main.kt
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