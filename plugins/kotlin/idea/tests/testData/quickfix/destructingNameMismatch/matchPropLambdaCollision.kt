// "Rename variable to 'productName'" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// SHOULD_FAIL_WITH: Variable 'productName' is already declared in lambda

data class Product (val id: String, val productName: String)

fun test (product: Product) {
    val (id: String, na<caret>me: String) = product
    run {
        val productName = "hello"
        print(name)
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations.RenameVariableToMatchPropertiesQuickFix