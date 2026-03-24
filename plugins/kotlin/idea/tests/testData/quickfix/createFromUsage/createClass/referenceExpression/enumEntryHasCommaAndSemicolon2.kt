// "Create enum constant 'C'" "true"
// K2_ERROR: Unresolved reference 'C'.
enum class Baz {
    A,
    B, ;
}

fun main() {
    Baz.C<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction