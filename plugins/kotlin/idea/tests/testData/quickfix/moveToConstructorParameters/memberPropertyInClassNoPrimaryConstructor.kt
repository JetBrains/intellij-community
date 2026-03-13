// "Move to constructor parameters" "true"
// K2_ERROR: Property must be initialized or be abstract.
open class A {
    <caret>val n: Int
}

class B : A()

fun test() {
    val a = A()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$MoveToConstructorParameters
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$MoveToConstructorParametersFix