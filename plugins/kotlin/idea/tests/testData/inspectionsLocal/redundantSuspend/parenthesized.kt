// PROBLEM: none
// WITH_STDLIB

object Foo {
    val f: suspend SequenceScope<Int>.(low: Int, high: Int) -> Unit = { low, high ->
        (low until high).forEach {
            yield(it)
        }
    }
}

<caret>suspend fun SequenceScope<Int>.f2(low: Int, high: Int) {
    (Foo.f)(low, high)
}
