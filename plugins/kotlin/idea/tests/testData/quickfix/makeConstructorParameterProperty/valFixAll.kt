// "Apply all 'Make constructor parameter a property' fixes in file" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

class A(foo: String) {
    fun bar() {
        val a = foo<caret>
    }
}

class B(bar: String) {
    fun baz() {
        val b = bar
    }
}

// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems
