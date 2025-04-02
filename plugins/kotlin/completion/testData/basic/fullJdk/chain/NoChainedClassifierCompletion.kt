// IGNORE_K1
// REGISTRY: kotlin.k2.chain.completion.enabled false

fun main() {
    DirectoryStream.<caret>
}

// ABSENT: { lookupString: "Filter", itemText: "DirectoryStream.Filter" }
// INVOCATION_COUNT: 0