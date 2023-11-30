// "Add 'open fun f()' to 'A'" "true"
open class A {
}
class B : A() {
    <caret>override fun f() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionToSupertypeFix