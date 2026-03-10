// "Add 'fun' modifier to 'I'" "true"
// K2_ERROR: Interface 'interface I : Any' does not have constructors.
interface I {
    fun f()
}

fun foo(i: I, j: Int) {}

fun test() {
    val x = foo(<caret>I {}, 2)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddFunModifierFixFactory$AddFunModifierFix