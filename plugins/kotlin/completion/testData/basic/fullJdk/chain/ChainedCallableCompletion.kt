// REGISTRY: kotlin.k2.chain.completion.enabled true

fun main() {
    Collections.<caret>
}

// ABSENT: Collections
// EXIST: { lookupString: "emptyList", itemText: "Collections.emptyList", tailText: "()", typeText: "kotlin.collections.(Mutable)List<T!>" }
// INVOCATION_COUNT: 0