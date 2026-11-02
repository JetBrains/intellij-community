// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// PROBLEM: Full-form name-based destructuring can use the short form
// FIX: Convert to short name-based destructuring form

data class Foo(val x: String, val y: Int)

fun test(foo: Foo) {
    foo.let { (<caret>val x, val y) -> x.length + y }
}
