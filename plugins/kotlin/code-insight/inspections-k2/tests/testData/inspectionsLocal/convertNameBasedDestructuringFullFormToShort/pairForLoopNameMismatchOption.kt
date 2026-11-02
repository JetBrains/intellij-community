// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// PROBLEM: Full-form name-based destructuring can use the short form
// FIX: Convert to short name-based destructuring form

fun test(values: List<Pair<String, Int>>) {
    for ((<caret>val first, val second) in values) {
        println(first.length + second)
    }
}
