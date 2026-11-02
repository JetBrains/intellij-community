// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// PROBLEM: Full-form name-based destructuring can use the short form
// FIX: Convert to short name-based destructuring form

fun test(values: Iterable<IndexedValue<String>>) {
    for ((<caret>val index, val value) in values) {
        println(index + value.length)
    }
}
