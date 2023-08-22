// "Change function signature to 'fun f(a: Int)'" "true"
open class A {
    open fun f(a: Int) {}
}

class B : A() {
    <caret>override fun <T : Number> f(a: T) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix