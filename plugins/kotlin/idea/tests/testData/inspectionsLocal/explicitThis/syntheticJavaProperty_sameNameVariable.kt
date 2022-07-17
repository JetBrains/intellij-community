// WITH_STDLIB
// PROBLEM: none

fun test() {

    val isB = true

    Foo().apply {
        <caret>this.isB = true
    }
}
