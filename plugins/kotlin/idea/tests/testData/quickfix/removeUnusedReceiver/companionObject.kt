// "Remove redundant receiver parameter" "false"

class A { companion object }

fun <caret>A.Companion.foo() = ""

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.RemoveReceiverParameterFix