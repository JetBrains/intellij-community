// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// PROBLEM: Full-form name-based destructuring can use the short form
// FIX: Convert to short name-based destructuring form

data class Foo(val x: String, val y: Int)

fun test() {
    (<caret>val /* keep x */ renamed = x, val y /* keep y */) = Foo("", 0)
}
