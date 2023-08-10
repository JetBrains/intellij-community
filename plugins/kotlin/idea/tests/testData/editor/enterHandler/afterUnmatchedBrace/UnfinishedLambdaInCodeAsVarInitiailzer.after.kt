// WITH_STDLIB
fun foo() {
    val v = run {
        <caret>foo()
    }

    print(1)
}
