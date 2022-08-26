fun foo(x: String) {}

fun test() {
    val variable = 1
    foo("", var<caret>)
}
