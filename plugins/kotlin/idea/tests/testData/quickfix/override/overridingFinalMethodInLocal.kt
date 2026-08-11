// "Make A.foo open" "true"
// K2_ERROR: OVERRIDING_FINAL_MEMBER
open class A {
    fun foo() {}
}

fun test() {
    val some = object : A() {
        <caret>override fun foo() {}
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeOverriddenMemberOpenFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.MakeOverriddenMemberOpenFixFactory$MakeOverriddenMemberOpenFix