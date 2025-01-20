// IS_APPLICABLE: false
// ERROR: None of the following functions can be called with the arguments supplied: <br>public fun foo(s: String, b: Boolean, c: Char): Unit defined in root package in file ambiguousCall.kt<br>public fun foo(s: String, b: Boolean, p: Int): Unit defined in root package in file ambiguousCall.kt
// K2_ERROR: None of the following candidates is applicable:<br>fun foo(s: String, b: Boolean, p: Int): Unit<br>fun foo(s: String, b: Boolean, c: Char): Unit
fun foo(s: String, b: Boolean, p: Int){}
fun foo(s: String, b: Boolean, c: Char){}

fun bar() {
    foo("", <caret>true)
}