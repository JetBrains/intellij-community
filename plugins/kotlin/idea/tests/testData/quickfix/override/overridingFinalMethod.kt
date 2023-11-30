// "Make A.foo open" "true"
open class A {
    fun foo() {}
}

class B : A() {
    override<caret> fun foo() {}
}
/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeOverriddenMemberOpenFix