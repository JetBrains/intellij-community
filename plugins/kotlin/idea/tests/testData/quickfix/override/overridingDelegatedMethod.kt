// "Make XX.foo open" "true"
interface X {
    fun foo()
}

interface XX : X {
    override final fun foo() {

    }
}

abstract class A(val y: XX) : X, XX by y {
}

class B(y: XX) : A(y) {
    override<caret> fun foo() {
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeOverriddenMemberOpenFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.MakeOverriddenMemberOpenFixFactory$MakeOverriddenMemberOpenFix