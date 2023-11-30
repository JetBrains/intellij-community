// "Remove useless cast" "true"
class A {
    fun foo() {}
}

fun test() {
    A().foo()
    ({ "" } as<caret> () -> String)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix