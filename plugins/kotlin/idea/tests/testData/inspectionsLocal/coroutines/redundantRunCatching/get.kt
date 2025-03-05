// WITH_STDLIB
// PROBLEM: none
// ERROR: Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: <br>public operator fun MatchGroupCollection.get(name: String): MatchGroup? defined in kotlin.text
// K2_ERROR: Unresolved reference 'get'.

fun foo() = runCatching<caret> { 42 }.get()
