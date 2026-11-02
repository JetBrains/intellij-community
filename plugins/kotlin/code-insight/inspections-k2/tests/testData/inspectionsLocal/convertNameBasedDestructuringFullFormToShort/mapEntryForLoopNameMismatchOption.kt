// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// PROBLEM: Full-form name-based destructuring can use the short form
// FIX: Convert to short name-based destructuring form

fun test(values: Map<String, Int>) {
    for ((<caret>val key, val value) in values) {
        println(key.length + value)
    }
}
