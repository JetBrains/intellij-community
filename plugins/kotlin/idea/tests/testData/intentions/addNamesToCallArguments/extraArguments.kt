// IS_APPLICABLE: false
// ERROR: Too many arguments for public fun foo(s: String, b: Boolean, p: Int): Unit defined in root package in file extraArguments.kt

fun foo(s: String, b: Boolean, p: Int){}

fun bar() {
    <caret>foo("", true, 1, 2)
}