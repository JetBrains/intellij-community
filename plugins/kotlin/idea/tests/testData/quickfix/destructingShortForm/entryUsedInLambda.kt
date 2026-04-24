// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Product(val id: String, val productName: String)

fun test(product: Product) {
    val (idValue, <caret>name) = product
    
    // Entry is used in lambda - should prevent renaming
    listOf(1, 2, 3).forEach { 
        println("$idValue: $it")
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix