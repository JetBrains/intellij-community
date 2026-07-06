// "Make 'foo' in A, X and Y open" "true"
// K2_ERROR: OVERRIDING_FINAL_MEMBER
// K2_ERROR: WRONG_MODIFIER_CONTAINING_DECLARATION
// K2_ERROR: WRONG_MODIFIER_CONTAINING_DECLARATION
open class A {
    fun foo() {}
}

interface X {
    final fun foo() {}
}

interface Y {
    final fun foo() {}
}

interface Z {
    fun foo() {}
}

class B : A(), X, Y, Z {
    override<caret> fun foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeOverriddenMemberOpenFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.MakeOverriddenMemberOpenFixFactory$MakeOverriddenMemberOpenFix