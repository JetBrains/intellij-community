// "Create function" "false"
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ERROR: The expression cannot be a selector (occur after a dot)
// ERROR: Type mismatch: inferred type is () -> Boolean but Boolean was expected
// ERROR: Type mismatch: inferred type is () -> Boolean but Boolean was expected
// ERROR: Unresolved reference: maximumSizeOfGroup
// K2_AFTER_ERROR: Return type mismatch: expected 'Boolean', actual 'Function0<Boolean>'.
// K2_AFTER_ERROR: The expression cannot be a selector (cannot occur after a dot).
// K2_AFTER_ERROR: Unresolved reference 'maximumSizeOfGroup'.

fun doSomethingStrangeWithCollection(collection: Collection<String>): Collection<String>? {
    val groupsByLength = collection.groupBy { s -> { s.length } }

    val maximumSizeOfGroup = groupsByLength.values.maxByOrNull { it.size }.
    return groupsByLength.values.firstOrNull { group -> {group.size == <caret>maximumSizeOfGroup} }
}
