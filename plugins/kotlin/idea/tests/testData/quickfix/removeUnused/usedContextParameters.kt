// "Safe delete parameter 'myContext'" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(myCo<caret>ntext: Int)
private fun m(): Int {
    return myContext
}

// IGNORE_K1