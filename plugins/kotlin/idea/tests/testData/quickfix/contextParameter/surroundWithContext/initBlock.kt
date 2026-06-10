// "Surround call with 'context(i)'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// K2_ERROR: No context argument for 'i: Int' found.
context(i: Int) fun bar() {}

class MyClass(val i: Int) {
    init {
        <caret>bar()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundCallWithContextFix