// "Create class 'Foo'" "false"
// ERROR: No value passed for parameter 's'
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: NO_VALUE_FOR_PARAMETER

class Foo(i: Int, s: String)

fun test() {
    val a = Foo(2<caret>)
}