// IGNORE_K1
// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
data class Product(val id: Int, val productName: String)

fun prod(product: Product) {
    (val id, val productName) = product
}

