// "Initialize with constructor parameter" "true"
open class A(s: String) {
    <caret>var n: Int
        get() = 1

    constructor(): this("")
    constructor(a: Int): this("" + a)
}

class B : A("")

fun test() {
    val a = A("")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$InitializeWithConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializeWithConstructorParameterFix