// PLATFORM: Common
// FILE: A.kt

expect fun foo(): String

fun useFoo() {
    foo()
}

// PLATFORM: Jvm
// FILE: B.kt

actual fun foo(): String = "JVM"

fun useFoo() {
    foo()
}

// PLATFORM: Js
// FILE: C.kt

actual fun foo(): String = "JS"

fun useFoo() {
    foo()
}
