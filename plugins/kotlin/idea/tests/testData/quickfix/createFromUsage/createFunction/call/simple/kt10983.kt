// "Create function" "false"
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ERROR: The expression cannot be a selector (occur after a dot)
// ERROR: Type mismatch: inferred type is () -> Boolean but Boolean was expected
// ERROR: Type mismatch: inferred type is () -> Boolean but Boolean was expected
// ERROR: Unresolved reference: maximumSizeOfGroup
// K2_AFTER_ERROR: ILLEGAL_SELECTOR
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: ILLEGAL_SELECTOR
// K2_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: UNRESOLVED_REFERENCE

fun doSomethingStrangeWithCollection(collection: Collection<String>): Collection<String>? {
    val groupsByLength = collection.groupBy { s -> { s.length } }

    val maximumSizeOfGroup = groupsByLength.values.maxByOrNull { it.size }.
    return groupsByLength.values.firstOrNull { group -> {group.size == <caret>maximumSizeOfGroup} }
}
