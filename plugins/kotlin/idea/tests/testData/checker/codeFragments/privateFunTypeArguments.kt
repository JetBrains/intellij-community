// BLOCK_CODE_FRAGMENT

fun foo() {
    <caret>val a = 1
}

class MyClass {
    private fun <T> privateFun(i: T): T = i
}