// FIR_IDENTICAL
// FIR_COMPARISON
fun foo(paramFirst: Int, paramSecond: Int) {}

fun test() {
    foo("", <caret>)
}

// ABSENT: "paramFirst ="
// EXIST: {"lookupString":"paramSecond =","tailText":" Int","itemText":"paramSecond ="}