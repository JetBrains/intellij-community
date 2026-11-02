// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// PROBLEM: Full-form name-based destructuring can use the short form
// FIX: Convert to short name-based destructuring form

data class Foo(val x: String, val y: Int)

fun test() {
    (<caret>val x, val y) = Foo("", 0)
}
