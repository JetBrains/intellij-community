// "Move to constructor parameters" "true"
annotation class foo

open class A(s: String) {
    <caret>private @foo     val /*1*/ n: /* 2 */ Int
}

class B : A("")

fun test() {
    val a = A("")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$MoveToConstructorParameters
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$MoveToConstructorParametersFix