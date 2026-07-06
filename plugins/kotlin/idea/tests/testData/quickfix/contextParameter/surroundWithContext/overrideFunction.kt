// "Surround call with 'context(i)'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: NO_CONTEXT_ARGUMENT
context(i: Int) fun bar() {}

abstract class Base {
    abstract fun foo(i: Int)
}

class Derived : Base() {
    override fun foo(i: Int) {
        <caret>bar()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundCallWithContextFix