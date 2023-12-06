// "Make XX.foo open" "true"
interface X {
    fun foo()
}

interface XX : X {
    override final fun foo() {

    }
}

interface Y : X, XX {
}

class B() : Y {
    override<caret> fun foo() {
    }
}
/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeOverriddenMemberOpenFix