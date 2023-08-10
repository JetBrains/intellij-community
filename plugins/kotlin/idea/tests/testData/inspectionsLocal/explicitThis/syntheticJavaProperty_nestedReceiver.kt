// WITH_STDLIB
// PROBLEM: none

fun test() {
    Foo().apply {
        Bar().run {
            <caret>this@apply.isB = true
        }
    }
}