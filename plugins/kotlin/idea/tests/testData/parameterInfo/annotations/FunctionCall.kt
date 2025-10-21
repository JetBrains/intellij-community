package test

annotation class Fancy

private fun abc(@Fancy foo: Int) {
}

fun foo() {
    abc(<caret>)
}
