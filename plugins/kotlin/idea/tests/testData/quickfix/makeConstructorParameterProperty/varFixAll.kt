// "Apply all 'Make constructor parameter a property' fixes in file" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

class A(foo: String) {
    fun update() {
        foo<caret> = ""
    }
}

class B(bar: String) {
    fun update() {
        bar = ""
    }
}

// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems
