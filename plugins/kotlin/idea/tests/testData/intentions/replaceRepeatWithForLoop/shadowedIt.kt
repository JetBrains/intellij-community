// WITH_STDLIB
fun foo() {
    listOf(1, 2).forEach {
        <caret>repeat(3) { innerIt ->
            println(it) // 'it' should NOT be replaced, belongs to forEach
            println(innerIt)
        }
    }
}