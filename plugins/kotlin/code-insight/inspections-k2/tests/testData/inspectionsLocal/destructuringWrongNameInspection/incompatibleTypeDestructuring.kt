// PROBLEM: Variable name 'productName' matches the name of a different component
// FIX: none
// K2_ERROR: Operator call 'component1()' returns 'Int', but 'String' is expected.
data class Product(val id: Int, val productName: String)

fun test(product: Product) {
    val (<caret>productName: String) = product
}