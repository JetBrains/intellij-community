// "Add 'fun' modifier to 'I'" "true"
interface I {
    fun f()
}

fun foo(i: I) {}

fun test() {
    val x = foo(<caret>I {})
}

// IGNORE_K2
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunModifierFix