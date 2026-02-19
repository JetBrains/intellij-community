// PROBLEM: none
// WITH_STDLIB
// IGNORE_FE10_BINDING_BY_FIR
fun test() {
    suspend<caret> fun f(value: Int) {}
    val executors = mutableListOf<suspend (Int) -> Unit> ()
    executors.add(::f)
}