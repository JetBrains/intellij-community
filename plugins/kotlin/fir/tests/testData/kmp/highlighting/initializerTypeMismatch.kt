// Issue: KT-65406
// PLATFORM: common
// FILE: common.kt

package tm

<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING]">expect</warning>
class MyClass {
    val base: String
}

// PLATFORM: linux
// FILE: linux.kt

package tm

<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING]">actual</warning>
class MyClass {
    actual val base: String <error descr="[INITIALIZER_TYPE_MISMATCH]">=</error> 42
}

// PLATFORM: jvm
// FILE: jvm.kt

package tm

<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING]">actual</warning>
class MyClass {
    actual val base: String <error descr="[INITIALIZER_TYPE_MISMATCH]">=</error> 42
}

// PLATFORM: MinGW
// FILE: win.kt

package tm

<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING]">actual</warning>
class MyClass {
    actual val base: String <error descr="[INITIALIZER_TYPE_MISMATCH]">=</error> 42
}

// PLATFORM: js
// FILE: js.kt
package tm

<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING]">actual</warning>
class MyClass {
    actual val base: String <error descr="[INITIALIZER_TYPE_MISMATCH]">=</error> 42
}
