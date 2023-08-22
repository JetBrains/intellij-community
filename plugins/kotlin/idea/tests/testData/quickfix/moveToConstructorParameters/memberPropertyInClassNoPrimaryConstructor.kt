// "Move to constructor parameters" "true"
open class A {
    <caret>val n: Int
}

class B : A()

fun test() {
    val a = A()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$MoveToConstructorParameters