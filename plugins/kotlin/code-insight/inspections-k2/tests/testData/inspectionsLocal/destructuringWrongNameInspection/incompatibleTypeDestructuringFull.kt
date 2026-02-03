// PROBLEM: none
// FIX: none
// K2_ERROR: The feature "name based destructuring" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xname-based-destructuring={only-syntax|name-mismatch|complete}', but note that no stability guarantees are provided.
data class Product(val id: Int, val productName: String)

fun test(product: Product) {
    (val <caret>productName: String) = product
}