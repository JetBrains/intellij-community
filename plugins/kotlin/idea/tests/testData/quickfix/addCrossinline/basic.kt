// "Add 'crossinline' to parameter 'block'" "true"

interface I {
    fun foo()
}

inline fun bar(block: () -> Unit) {
    object : I {
        override fun foo() {
            <caret>block()
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineModifierFix