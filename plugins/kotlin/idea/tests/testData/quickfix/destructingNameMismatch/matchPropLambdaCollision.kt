// "Rename variable to 'productName'" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Product (val id: String, val productName: String)

fun test (product: Product) {
    val (id: String, na<caret>me: String) = product
    run {
        val productName = "hello"
        print(name)
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.declarations.RenameVariableToMatchPropertiesQuickFix