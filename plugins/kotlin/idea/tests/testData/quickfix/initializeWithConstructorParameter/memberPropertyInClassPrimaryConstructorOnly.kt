// "Initialize with constructor parameter" "true"
open class A(s: String) {
    <caret>var n: Int
        get() = 1
}

class B : A("")

fun test() {
    val a = A("")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$InitializeWithConstructorParameter