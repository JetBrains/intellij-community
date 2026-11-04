// Issue: KTIJ-20701
// PLATFORM: Common
// FILE: pack/common.kt

package pack

import kotlin.jvm.JvmOverloads

@JvmOverloads
expect fun withDefaultsInExpect(flag: Boolean = false): Boolean

expect fun withoutJvmOverloads(flag: Boolean = false): Boolean

// PLATFORM: Jvm
// FILE: pack/jvm.kt

package pack

@JvmOverloads
actual fun withDefaultsInExpect(flag: Boolean): Boolean = flag

actual fun withoutJvmOverloads(flag: Boolean): Boolean = flag

// FILE: pack/JavaUsage.java

package pack;

public class JavaUsage {
    public static boolean callOverloads() {
        return JvmKt.withDefaultsInExpect() && JvmKt.withDefaultsInExpect(true) && JvmKt.withoutJvmOverloads<error descr="Expected 1 argument but found 0">()</error>;
    }
}
