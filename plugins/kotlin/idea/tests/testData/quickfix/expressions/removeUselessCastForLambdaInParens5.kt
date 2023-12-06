// "Remove useless cast" "true"
open class A

fun test() {
    class B : A()
    ({ "" } as<caret> () -> String)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix