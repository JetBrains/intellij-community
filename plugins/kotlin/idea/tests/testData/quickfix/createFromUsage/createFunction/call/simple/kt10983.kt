// "Create function" "false"
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ERROR: The expression cannot be a selector (occur after a dot)
// ERROR: Type mismatch: inferred type is () -> Boolean but Boolean was expected
// ERROR: Type mismatch: inferred type is () -> Boolean but Boolean was expected
// ERROR: Unresolved reference: maximumSizeOfGroup

fun doSomethingStrangeWithCollection(collection: Collection<String>): Collection<String>? {
    val groupsByLength = collection.groupBy { s -> { s.length } }

    val maximumSizeOfGroup = groupsByLength.values.maxByOrNull { it.size }.
    return groupsByLength.values.firstOrNull { group -> {group.size == <caret>maximumSizeOfGroup} }
}
