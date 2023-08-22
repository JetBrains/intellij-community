// "Add 'fun' modifier to 'I'" "true"
interface I {
    fun f()
}

fun foo(f: () -> Unit, i: I) {}

fun test() {
    val x = foo({}, <caret>I {})
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunModifierFix