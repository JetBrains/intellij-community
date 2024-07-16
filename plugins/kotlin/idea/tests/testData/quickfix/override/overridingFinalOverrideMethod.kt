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

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeOverriddenMemberOpenFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.MakeOverriddenMemberOpenFixFactory$MakeOverriddenMemberOpenFix