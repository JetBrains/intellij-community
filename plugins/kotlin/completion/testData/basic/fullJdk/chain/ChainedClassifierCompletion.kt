// REGISTRY: kotlin.k2.chain.completion.enabled true

fun main() {
    DirectoryStream.<caret>
}

// EXIST: { lookupString: "Filter", itemText: "DirectoryStream.Filter", tailText: "<T> (java.nio.file.DirectoryStream)" }
// INVOCATION_COUNT: 0