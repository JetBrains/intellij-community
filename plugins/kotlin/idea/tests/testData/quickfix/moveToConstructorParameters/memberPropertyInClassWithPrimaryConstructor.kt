// "Move to constructor parameters" "true"
open class A(s: String) {
    <caret>val n: Int

    constructor(a: Int): this("")
}

class B : A("")

fun test() {
    val a = A("")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$MoveToConstructorParameters