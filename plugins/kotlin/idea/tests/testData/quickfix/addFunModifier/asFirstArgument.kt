// "Add 'fun' modifier to 'I'" "true"
interface I {
    fun f()
}

fun foo(i: I, j: Int) {}

fun test() {
    val x = foo(<caret>I {}, 2)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunModifierFix