// "Make B.foo open" "true"
abstract class A {
    abstract fun foo()
}

open class B : A() {
    final override fun foo() {}
}

class C : B() {
    override<caret> fun foo() {}
}
/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeOverriddenMemberOpenFix