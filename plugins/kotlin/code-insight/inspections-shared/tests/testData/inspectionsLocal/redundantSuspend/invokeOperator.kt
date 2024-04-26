// PROBLEM: none
// WITH_STDLIB
// IGNORE_FE10_BINDING_BY_FIR

interface I {
    suspend operator fun invoke()
}

suspend<caret> fun test1(i: I) {
    listOf(i).firstOrNull()!!()
}
