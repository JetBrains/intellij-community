// COMPILER_ARGUMENTS: -Xcompanion-blocks
// SHOULD_FAIL_WITH: Access to a super member won't be processed: super.parentFun()

open class Parent {
    open fun parentFun() = 0
}

class Child : Parent() {
    override fun parentFun() = 1

    fun <caret>foo() = super.parentFun()
}
