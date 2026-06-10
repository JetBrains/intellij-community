// "Surround call with 'with(i)'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// API_VERSION: 2.1
// K2_ERROR: No context argument for 'i: Int' found.
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