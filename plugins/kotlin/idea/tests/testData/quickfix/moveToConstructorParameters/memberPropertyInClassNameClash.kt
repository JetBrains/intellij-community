// "Move to constructor parameters" "true"
// SHOULD_FAIL_WITH: Duplicating parameter 'n'
// K2_AFTER_ERROR: Property must be initialized or be abstract.
open class A(n: Int) {
    <caret>val n: Int
}

fun test() {
    val a = A(0)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$MoveToConstructorParameters
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$MoveToConstructorParametersFix