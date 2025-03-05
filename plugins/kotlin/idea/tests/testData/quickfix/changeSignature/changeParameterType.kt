// "Change the signature of function 'mmm'" "false"
// DISABLE_ERRORS
// ACTION: Add 'toString()' call
// ACTION: Change parameter 's' type of function 'mmm' to 'Int'
// ACTION: Do not show hints for current method

fun m() {
    mmm(<caret>1)
}

fun mmm(s: String) {
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$applicator$1
// IGNORE_K2