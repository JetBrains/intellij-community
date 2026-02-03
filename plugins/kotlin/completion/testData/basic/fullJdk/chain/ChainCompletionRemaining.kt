// REGISTRY: kotlin.k2.chain.completion.enabled true

fun main() {
    "".val<caret>
}

// EXIST: { lookupString: ".val", itemText: "val" }
// INVOCATION_COUNT: 0