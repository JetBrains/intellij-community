// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false
class MyList : AbstractList<Int>() {
    override val size: Int get() = 1
    override fun get(<caret>index: Int): Int = 0
}