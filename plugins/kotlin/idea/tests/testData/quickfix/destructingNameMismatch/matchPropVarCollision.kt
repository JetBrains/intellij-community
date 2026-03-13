// "Rename variable to 'productName'" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// SHOULD_FAIL_WITH: Parameter 'productName' is already declared in function 'test'

data class Product(val id: String, val productName: String)

fun test(productName: String, product: Product) {
    val (id, n<caret>ame) = product
    println(productName)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations.RenameVariableToMatchPropertiesQuickFix