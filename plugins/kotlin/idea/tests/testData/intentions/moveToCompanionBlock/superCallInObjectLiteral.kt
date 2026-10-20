// COMPILER_ARGUMENTS: -Xcompanion-blocks

open class Parent {
    open fun parentFun() = 0
}

class A {
    fun <caret>foo(): Parent = object : Parent() {
        override fun parentFun() = super.parentFun() + 1
    }
}
