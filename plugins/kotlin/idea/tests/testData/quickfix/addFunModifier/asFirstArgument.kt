// "Add 'fun' modifier to 'I'" "true"
interface I {
    fun f()
}

fun foo(i: I, j: Int) {}

fun test() {
    val x = foo(<caret>I {}, 2)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddFunModifierFixFactory$AddFunModifierFix