// PROBLEM: none
// WITH_STDLIB

fun test() {
    val foo = 1
    val bar = 2

    <caret>String.format("""foo is %s, bar is %s.%n""", foo, bar)
}