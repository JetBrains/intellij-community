// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false
open class Base {
    override fun equals(other: <caret>Any?): Boolean = true
}