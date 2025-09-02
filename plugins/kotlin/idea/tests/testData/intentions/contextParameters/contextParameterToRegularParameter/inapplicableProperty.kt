// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xcontext-parameters

context(<caret>c1: Int)
val v: String
    get() = "v$c1"
