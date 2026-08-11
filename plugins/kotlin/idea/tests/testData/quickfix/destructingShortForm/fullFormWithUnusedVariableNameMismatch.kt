// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch

data class Product(val id: String, val productName: String)

fun test(product: Product) {
    val (<caret>myId, _) = product
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.DestructuringFormFactory$ConvertNameBasedDestructuringToFullFormFix