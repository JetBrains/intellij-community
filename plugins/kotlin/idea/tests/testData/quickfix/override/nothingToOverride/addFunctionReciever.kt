// "Change function signature to 'fun Int.foo(a: String)'" "true"
abstract class C {
    abstract fun Int.foo(a: String)
}

class B : C() {
    <caret>override fun foo(a: Int) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix