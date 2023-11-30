// "Add 'fun' modifier to 'I'" "true"
interface I {
    fun f()
}

fun test() {
    val x = <caret>I {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunModifierFix