// "Make A.foo open" "true"
open class A {
    fun foo() {}
}

fun test() {
    val some = object : A() {
        <caret>override fun foo() {}
    }
}
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeOverriddenMemberOpenFix