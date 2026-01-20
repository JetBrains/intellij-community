// "class org.jetbrains.kotlin.idea.quickfix.RenameParameterToMatchOverriddenMethodFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParenthesisFix" "false"
// "Rename parameter to match overridden method" "false"
abstract class A {
    abstract fun foo(arg : Int) : Int;
}

interface X {
    fun foo(agr: Int) : Int;
}

class B : A(), X {
    override fun foo(arg<caret>: Int) : Int {
        val x = arg + arg
        return arg
    }
}
