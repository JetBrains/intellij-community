// WITH_STDLIB
fun f() = <caret>with(Unit) {
    42
}

val answer: Int = f()
