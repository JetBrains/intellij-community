// "Safe delete parameter 'myContext'" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(myCo<caret>ntext: Int)
private fun m(): Int {
    return n
}

context(myContext: Int)
private val n: Int
    get() = 42

// IGNORE_K1