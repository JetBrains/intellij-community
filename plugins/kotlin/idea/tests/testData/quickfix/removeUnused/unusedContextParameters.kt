// "Safe delete parameter 'myContext'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(myCo<caret>ntext: Int)
private fun m(): Int {
    return with(42) {
        n()
    }
}

context(myContext: Int)
private fun n(): Int {
    return myContext
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.highlighting.SafeDeleteFix