// "Remove @JvmField annotation" "true"
// IGNORE_FIR
// WITH_STDLIB
class Foo {
    <caret>@JvmField private val bar = 0
}