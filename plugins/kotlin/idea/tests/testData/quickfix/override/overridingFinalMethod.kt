// "Make A.foo open" "true"
// K2_ERROR: OVERRIDING_FINAL_MEMBER
open class A {
    fun foo() {}
}

class B : A() {
    override<caret> fun foo() {}
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.MakeOverriddenMemberOpenFixFactory$MakeOverriddenMemberOpenFix