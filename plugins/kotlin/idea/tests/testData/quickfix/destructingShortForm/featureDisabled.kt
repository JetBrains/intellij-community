// "Convert to a full name-based destructuring form" "false"

data class Product(val id: String, val productName: String)

fun test(product: Product) {
    val (id, <caret>name) = product
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix