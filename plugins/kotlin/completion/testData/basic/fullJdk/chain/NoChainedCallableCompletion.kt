// IGNORE_K1
// REGISTRY: kotlin.k2.chain.completion.enabled false

fun main() {
    Collections.<caret>
}

// ABSENT: { lookupString: "emptyList", itemText: "Collections.emptyList" }
// INVOCATION_COUNT: 0