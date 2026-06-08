// "Add context parameter to function" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters

// K2_ERROR: No context argument for 'i: Int' found.
// K2_AFTER_ERROR: No context argument for 'i: Int' found.
context(i: Int) fun bar() {}

abstract class Base {
    abstract fun foo()
}

class Derived : Base() {
    override fun foo() {
        <caret>bar()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix