// PLATFORM: Common
// FILE: common.kt
// MAIN

expect fun aaabbbccc()

fun foo() {
    aaa<caret>bbbccc()
}

// INVOCATION_COUNT: 1
// EXIST: aaabbbccc

// PLATFORM: Jvm
// FILE: jvm.kt

actual fun aaabbbccc() = Unit