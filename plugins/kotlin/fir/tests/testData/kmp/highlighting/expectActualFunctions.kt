// PLATFORM: Common
// FILE: common.kt

expect fun foo(): String

fun useFoo() {
    foo()
}

// PLATFORM: Jvm
// FILE: jvm.kt

actual fun foo(): String = "JVM"

fun useFoo() {
    foo()
}

// PLATFORM: Js
// FILE: js.kt

actual fun foo(): String = "JS"

fun useFoo() {
    foo()
}
