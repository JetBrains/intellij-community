// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// PROBLEM: Full-form name-based destructuring can use the short form
// FIX: Convert to short name-based destructuring form

data class Foo(val x: String, val y: Int)

fun test(values: List<Foo>) {
    for ((<caret>val x, val y) in values) {
        println(x.length + y)
    }
}
