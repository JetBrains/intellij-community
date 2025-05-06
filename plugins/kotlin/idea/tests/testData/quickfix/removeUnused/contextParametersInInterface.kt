// "Safe delete parameter 'myContext'" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

interface A {
    context(myCo<caret>ntext: Int)
    fun m(): Int
}

// IGNORE_K1